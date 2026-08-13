package com.healingplanet.ai.service;

import com.healingplanet.ai.domain.DiseaseDetection;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.domain.RagResponse;
import com.healingplanet.ai.retrieval.DiseaseDetectorClient;
import com.healingplanet.ai.retrieval.DiseaseKnowledgeRetriever;
import com.healingplanet.ai.retrieval.PlantStateRetriever;
import com.healingplanet.ai.retrieval.SensorConsistencyAnalyzer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MultimodalDiagnosisService {
    private static final String SYSTEM_PROMPT = """
            你是 Healing Planet 的植物病害辅助分析助手。只根据本次提供的视觉、病害知识和传感器证据回答。
            1. 视觉模型结果只是候选，不得表述为确诊；每个事实性结论必须引用 [E1] 形式的现有证据。
            2. 处理与预防建议只能来自 DISEASE_KNOWLEDGE，不得从模型记忆补全。
            3. 必须解释 SENSOR_CONSISTENCY 是支持、冲突还是无法判断；冲突时建议复查根系、叶背或寻求专业检验。
            4. 缺少病害知识或状态数据时要明确说明证据不足；不执行设备操作。使用简洁中文。
            """;

    private final DiseaseDetectorClient detector;
    private final DiseaseKnowledgeRetriever diseaseRetriever;
    private final PlantStateRetriever stateRetriever;
    private final SensorConsistencyAnalyzer consistencyAnalyzer;
    private final PromptContextBuilder contextBuilder;
    private final ChatClient chatClient;

    public MultimodalDiagnosisService(DiseaseDetectorClient detector, DiseaseKnowledgeRetriever diseaseRetriever,
                                      PlantStateRetriever stateRetriever, SensorConsistencyAnalyzer consistencyAnalyzer,
                                      PromptContextBuilder contextBuilder, ChatClient chatClient) {
        this.detector = detector;
        this.diseaseRetriever = diseaseRetriever;
        this.stateRetriever = stateRetriever;
        this.consistencyAnalyzer = consistencyAnalyzer;
        this.contextBuilder = contextBuilder;
        this.chatClient = chatClient;
    }

    public RagResponse diagnose(FilePart image, Long userId, Long plantInstanceId,
                                String canonicalPlantId, String question) {
        if (userId == null || plantInstanceId == null) {
            throw new IllegalArgumentException("多模态诊断需要 userId 和 plantInstanceId 以校验植物归属并读取状态");
        }
        String queryText = question == null || question.isBlank() ? "请根据图片和植物状态辅助分析可能的病害" : question.trim();
        RagQuery query = new RagQuery(queryText, userId, plantInstanceId, canonicalPlantId,
                QueryIntent.DISEASE_DIAGNOSIS, List.of(), Map.of());
        DiseaseDetection detection = detector.detect(image);
        List<Evidence> state = stateRetriever.retrieve(query);
        List<Evidence> disease = diseaseRetriever.retrieve(detection, query);
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(visualEvidence(detection));
        evidence.addAll(disease);
        evidence.addAll(state);
        disease.stream().findFirst().ifPresent(item -> evidence.add(consistencyAnalyzer.analyze(item, state)));

        if (detection.healthy()) {
            return answer(queryText, evidence, "视觉模型未识别到病害候选，但这不代表排除早期或模型范围外的问题。");
        }
        if (disease.isEmpty()) {
            return new RagResponse("视觉模型给出了病害候选，但知识库中没有检索到足够的可信病害证据，因此暂不提供处理建议。", List.copyOf(evidence));
        }
        return answer(queryText, evidence, null);
    }

    private RagResponse answer(String query, List<Evidence> evidence, String note) {
        String prompt = "用户问题：\n" + query + (note == null ? "" : "\n\n额外约束：" + note)
                + "\n\n可用证据：\n" + contextBuilder.build(evidence);
        String content = chatClient.prompt().system(SYSTEM_PROMPT).user(prompt).call().content();
        return new RagResponse(content, List.copyOf(evidence));
    }

    private Evidence visualEvidence(DiseaseDetection detection) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "label", detection.label());
        put(metadata, "className", detection.className());
        put(metadata, "diseaseName", detection.diseaseName());
        put(metadata, "cropName", detection.cropName());
        put(metadata, "confidence", detection.confidence());
        metadata.put("healthy", detection.healthy());
        String confidence = detection.confidence() == null ? "模型未返回" : String.format(java.util.Locale.ROOT, "%.3f", detection.confidence());
        String content = "视觉模型候选：%s\n作物候选：%s\n置信度：%s\n注意：该结果只是感知候选，不是医学或植物病理确诊。"
                .formatted(detection.diseaseName(), detection.cropName(), confidence);
        return new Evidence("visual:" + detection.detectedAt().toEpochMilli(), EvidenceType.VISUAL_OBSERVATION,
                "detector:" + (detection.label() == null ? "unknown" : detection.label()), "DISEASE_MODEL",
                "图片病害感知候选", content, detection.confidence(), null,
                0.7, detection.confidence(), metadata, detection.detectedAt());
    }

    private void put(Map<String, Object> map, String key, Object value) { if (value != null) map.put(key, value); }
}
