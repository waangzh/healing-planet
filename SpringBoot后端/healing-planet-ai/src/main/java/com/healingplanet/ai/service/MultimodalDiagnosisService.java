package com.healingplanet.ai.service;

import com.healingplanet.ai.config.RagChatOptions;
import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.config.RagRuntimeConfig;
import com.healingplanet.ai.config.RagRuntimeConfigProvider;
import com.healingplanet.ai.config.RagRuntimeSnapshot;
import com.healingplanet.ai.domain.DiseaseDetection;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.ImageAttachment;
import com.healingplanet.ai.domain.MultimodalRagResponse;
import com.healingplanet.ai.domain.MultimodalRoute;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.domain.VisualObservation;
import com.healingplanet.ai.retrieval.DiseaseDetectorClient;
import com.healingplanet.ai.retrieval.DiseaseKnowledgeRetriever;
import com.healingplanet.ai.retrieval.PlantStateRetriever;
import com.healingplanet.ai.retrieval.SensorConsistencyAnalyzer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class MultimodalDiagnosisService {
    private static final String SYSTEM_PROMPT = """
            你是 Healing Planet 的植物病害辅助分析助手。只根据本次提供的视觉、病害知识和传感器证据回答。
            1. 视觉模型结果只是候选，不得表述为确诊；每个事实性结论必须引用 [E1] 形式的现有证据。
            2. 处理与预防建议只能来自 DISEASE_KNOWLEDGE，不得从模型记忆补全。
            3. 必须解释 SENSOR_CONSISTENCY 是支持、冲突还是无法判断；冲突时建议复查根系、叶背或寻求专业检验。
            4. 缺少病害知识或状态数据时要明确说明证据不足；不执行设备操作。使用简洁中文。
            """;
    private static final String OBSERVATION_PROMPT = """
            你是植物图片观察与路由器。只记录图片中可见内容，不给出治疗方案，不把猜测当作事实。
            按 VisualObservation 结构返回：
            - 病斑、黄叶、虫害、腐烂等需要专业诊断时推荐 DISEASE_DIAGNOSIS；
            - 标签、说明书、包装、仪表或截图文字为主时推荐 OCR；
            - 花盆、土壤、整株长势或其他一般图片问题推荐 GENERAL_VISION。
            必须描述颜色变化、病斑形状和分布、叶缘叶脉、可见虫体、图片质量和不确定性；看不清时明确写无法判断。
            searchQuery 应是适合检索植物养护知识的简短中文查询。
            """;
    private static final String GENERAL_SYSTEM_PROMPT = """
            你是 Healing Planet 的多模态植物助手。结合用户图片和给定证据回答。
            1. 每个事实性结论都必须引用现有 [E1] 编号，不能编造引用。
            2. 图片中看不清的内容必须说明不确定；不要把视觉观察表述为确诊。
            3. 处理建议必须有养护或病害知识证据支持；证据不足时说明需要补充什么。
            4. OCR 场景应忠实转写可见文字，不臆造被遮挡内容。
            5. 不执行设备操作，使用简洁自然的中文。
            """;

    private final DiseaseDetectorClient detector;
    private final DiseaseKnowledgeRetriever diseaseRetriever;
    private final PlantStateRetriever stateRetriever;
    private final SensorConsistencyAnalyzer consistencyAnalyzer;
    private final PromptContextBuilder contextBuilder;
    private final ChatClient chatClient;
    private final RagService ragService;
    private final ImageAttachmentStore attachmentStore;
    private final MultimodalRouter multimodalRouter;
    private final RagRuntimeConfigProvider runtimeConfigProvider;
    private final RagChatOptions chatOptions;

    @Autowired
    public MultimodalDiagnosisService(DiseaseDetectorClient detector, DiseaseKnowledgeRetriever diseaseRetriever,
                                      PlantStateRetriever stateRetriever, SensorConsistencyAnalyzer consistencyAnalyzer,
                                      PromptContextBuilder contextBuilder, ChatClient chatClient, RagService ragService,
                                      ImageAttachmentStore attachmentStore, MultimodalRouter multimodalRouter,
                                      RagRuntimeConfigProvider runtimeConfigProvider, RagChatOptions chatOptions) {
        this.detector = detector;
        this.diseaseRetriever = diseaseRetriever;
        this.stateRetriever = stateRetriever;
        this.consistencyAnalyzer = consistencyAnalyzer;
        this.contextBuilder = contextBuilder;
        this.chatClient = chatClient;
        this.ragService = ragService;
        this.attachmentStore = attachmentStore;
        this.multimodalRouter = multimodalRouter;
        this.runtimeConfigProvider = runtimeConfigProvider;
        this.chatOptions = chatOptions;
    }

    public MultimodalDiagnosisService(DiseaseDetectorClient detector, DiseaseKnowledgeRetriever diseaseRetriever,
                                      PlantStateRetriever stateRetriever, SensorConsistencyAnalyzer consistencyAnalyzer,
                                      PromptContextBuilder contextBuilder, ChatClient chatClient, RagService ragService,
                                      ImageAttachmentStore attachmentStore, MultimodalRouter multimodalRouter) {
        this(detector, diseaseRetriever, stateRetriever, consistencyAnalyzer, contextBuilder, chatClient, ragService,
                attachmentStore, multimodalRouter, new RagRuntimeConfigProvider(new RagProperties()), new RagChatOptions());
    }

    public MultimodalRagResponse analyze(FilePart image, String attachmentId, Long userId, Long plantInstanceId,
                                         String canonicalPlantId, String question, MultimodalRoute requestedRoute) {
        RagRuntimeSnapshot runtime = runtimeConfigProvider.runtimeSnapshot();
        RagRuntimeConfig config = runtime.config();
        String queryText = question == null || question.isBlank() ? "请分析这张植物图片" : question.trim();
        ImageAttachment attachment = attachmentStore.resolve(image, attachmentId);
        VisualObservation observation = attachment.observation();
        if (observation == null) {
            observation = observe(attachment, queryText, config);
            ImageAttachment updated = attachmentStore.updateObservation(attachment.id(), observation);
            if (updated != null) attachment = updated;
        }
        MultimodalRoute route = multimodalRouter.route(requestedRoute, queryText, observation);
        return route == MultimodalRoute.DISEASE_DIAGNOSIS
                ? diagnose(attachment, userId, plantInstanceId, canonicalPlantId, queryText, observation, runtime)
                : answerGeneral(attachment, userId, plantInstanceId, canonicalPlantId, queryText, observation,
                route, runtime);
    }

    private MultimodalRagResponse diagnose(ImageAttachment attachment, Long userId, Long plantInstanceId,
                                            String canonicalPlantId, String queryText,
                                            VisualObservation observation, RagRuntimeSnapshot runtime) {
        RagRuntimeConfig config = runtime.config();
        RagQuery query = new RagQuery(queryText, userId, plantInstanceId, canonicalPlantId,
                QueryIntent.DISEASE_DIAGNOSIS, List.of(), Map.of());
        DiseaseDetection detection = attachment.detection();
        String classifierNote = null;
        if (detection == null) {
            try {
                detection = detector.detect(attachment);
            } catch (IllegalStateException exception) {
                detection = detectionFromObservation(observation);
                classifierNote = "专用病害分类器暂时不可用，本轮已降级为多模态视觉观察。";
            }
            ImageAttachment updated = attachmentStore.updateDetection(attachment.id(), detection);
            if (updated != null) attachment = updated;
        }
        List<Evidence> state = stateRetriever.retrieve(query);
        List<Evidence> disease = diseaseRetriever.retrieve(detection, query, runtime);
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(visualEvidence(observation, detection, classifierNote));
        evidence.addAll(disease);
        evidence.addAll(state);
        disease.stream().findFirst().ifPresent(item -> evidence.add(consistencyAnalyzer.analyze(item, state)));

        String note = classifierNote;
        if (detection.healthy()) {
            note = joinNote(note, "视觉模型未识别到病害候选，但这不代表排除早期或模型范围外的问题。");
        }
        if (disease.isEmpty()) {
            note = joinNote(note, "知识库中没有检索到足够的可信病害证据，不得给出具体用药或处理方案。");
        }
        if (userId == null || plantInstanceId == null) {
            note = joinNote(note, "未选择植物，本轮未结合传感器数据，只能依据图片和知识库分析。");
        }
        return response(answerWithImage(SYSTEM_PROMPT, queryText, evidence, note, attachment, config), evidence,
                attachment, MultimodalRoute.DISEASE_DIAGNOSIS, observation, hasStateEvidence(state), note);
    }

    private MultimodalRagResponse answerGeneral(ImageAttachment attachment, Long userId, Long plantInstanceId,
                                                 String canonicalPlantId, String queryText,
                                                 VisualObservation observation, MultimodalRoute route,
                                                 RagRuntimeSnapshot runtime) {
        RagRuntimeConfig config = runtime.config();
        String retrievalQuery = Stream.of(queryText, observation.searchQuery(), observation.summary(),
                        observation.recognizedText())
                .filter(value -> value != null && !value.isBlank()).distinct().reduce((a, b) -> a + " " + b)
                .orElse(queryText);
        RagQuery query = new RagQuery(retrievalQuery, userId, plantInstanceId, canonicalPlantId,
                null, List.of(), Map.of());
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(visualEvidence(observation, null, null));
        evidence.addAll(ragService.search(query, runtime));
        boolean stateUsed = hasStateEvidence(evidence);
        String note = userId == null || plantInstanceId == null
                ? "未选择植物，本轮未结合传感器数据。" : null;
        return response(answerWithImage(GENERAL_SYSTEM_PROMPT, queryText, evidence, note, attachment, config), evidence,
                attachment, route, observation, stateUsed, note);
    }

    private VisualObservation observe(ImageAttachment attachment, String query, RagRuntimeConfig config) {
        try {
            VisualObservation result = chatClient.prompt().options(chatOptions.from(config)).system(OBSERVATION_PROMPT)
                    .user(user -> user.text("用户问题：" + query)
                            .media(MimeTypeUtils.parseMimeType(attachment.contentType()), attachment.resource()))
                    .call().entity(VisualObservation.class);
            if (result != null) return result;
        } catch (RuntimeException ignored) {
            // 结构化转换失败时仍保留原图给最终多模态回答，避免整条链路因路由失败中断。
        }
        return new VisualObservation(MultimodalRoute.GENERAL_VISION, "植物图片", null,
                "结构化视觉观察暂不可用，请由最终回答直接查看原图。", null, null, null,
                null, null, null, "待确认", "结构化观察失败", query);
    }

    private String answerWithImage(String systemPrompt, String query, List<Evidence> evidence, String note,
                                   ImageAttachment attachment, RagRuntimeConfig config) {
        String prompt = "用户问题：\n" + query + (note == null ? "" : "\n\n额外约束：" + note)
                + "\n\n可用证据：\n" + contextBuilder.build(evidence);
        return chatClient.prompt().options(chatOptions.from(config)).system(systemPrompt)
                .user(user -> user.text(prompt)
                        .media(MimeTypeUtils.parseMimeType(attachment.contentType()), attachment.resource()))
                .call().content();
    }

    private MultimodalRagResponse response(String answer, List<Evidence> evidence, ImageAttachment attachment,
                                            MultimodalRoute route, VisualObservation observation,
                                            boolean stateUsed, String extraNotice) {
        String engine = switch (route) {
            case DISEASE_DIAGNOSIS -> "已使用叶片诊断引擎。";
            case OCR -> "已使用图片文字识别与知识问答。";
            default -> "已使用通用视觉问答。";
        };
        String notice = engine + (extraNotice == null ? "" : extraNotice)
                + " 图片仅在 AI 服务内存中临时保留，约 " + Math.max(1, attachmentStore.ttlSeconds() / 60)
                + " 分钟后自动失效。";
        return new MultimodalRagResponse(answer, List.copyOf(evidence), attachment.id(), route, observation,
                stateUsed, attachmentStore.ttlSeconds(), notice);
    }

    private Evidence visualEvidence(VisualObservation observation, DiseaseDetection detection, String classifierNote) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "route", observation.recommendedRoute());
        put(metadata, "plantName", observation.plantName());
        put(metadata, "imageQuality", observation.imageQuality());
        if (detection != null) {
            put(metadata, "label", detection.label());
            put(metadata, "className", detection.className());
            put(metadata, "diseaseName", detection.diseaseName());
            put(metadata, "confidence", detection.confidence());
            metadata.put("healthy", detection.healthy());
        }
        List<String> lines = new ArrayList<>();
        addLine(lines, "整体观察", observation.summary());
        addLine(lines, "颜色变化", observation.colorChanges());
        addLine(lines, "病斑形状与分布", observation.lesionShapeAndDistribution());
        addLine(lines, "叶缘与叶脉", observation.leafEdgeAndVein());
        addLine(lines, "可见虫体", observation.visiblePests());
        addLine(lines, "识别文字", observation.recognizedText());
        addLine(lines, "图片质量", observation.imageQuality());
        addLine(lines, "不确定性", observation.uncertainty());
        if (detection != null) {
            String confidence = detection.confidence() == null ? "模型未返回"
                    : String.format(java.util.Locale.ROOT, "%.3f", detection.confidence());
            lines.add("专用分类器候选：" + safe(detection.diseaseName()) + "；作物候选："
                    + safe(detection.cropName()) + "；置信度：" + confidence);
        }
        if (classifierNote != null) lines.add(classifierNote);
        lines.add("注意：视觉结果只是观察与感知候选，不是植物病理确诊。");
        Instant timestamp = detection == null ? Instant.now() : detection.detectedAt();
        Double score = detection == null ? 0.7 : detection.confidence();
        return new Evidence("visual:" + timestamp.toEpochMilli(), EvidenceType.VISUAL_OBSERVATION,
                detection == null ? "multimodal-model" : "detector:" + safe(detection.label()),
                "MULTIMODAL_MODEL", "结构化图片观察", String.join("\n", lines), score, null,
                0.7, score, metadata, timestamp);
    }

    private DiseaseDetection detectionFromObservation(VisualObservation observation) {
        return new DiseaseDetection(null, observation.suspectedIssue(), observation.suspectedIssue(),
                observation.plantName(), null, false, Instant.now());
    }

    private boolean hasStateEvidence(List<Evidence> evidence) {
        return evidence.stream().anyMatch(item -> item.type() == EvidenceType.LIVE_STATE
                || item.type() == EvidenceType.SENSOR_HISTORY);
    }

    private String joinNote(String first, String second) {
        return first == null || first.isBlank() ? second : first + " " + second;
    }

    private void addLine(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) lines.add(label + "：" + value);
    }

    private String safe(Object value) { return value == null ? "未知" : String.valueOf(value); }
    private void put(Map<String, Object> map, String key, Object value) { if (value != null) map.put(key, value); }
}
