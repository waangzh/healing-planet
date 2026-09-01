package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class DiseaseKnowledgeConverter {

    private static final String INDEX_VERSION = "logical-evidence-v1";
    private final ChunkPolicy chunkPolicy;

    public DiseaseKnowledgeConverter() {
        this(new ChunkPolicy(new RagProperties()));
    }

    @Autowired
    public DiseaseKnowledgeConverter(ChunkPolicy chunkPolicy) {
        this.chunkPolicy = chunkPolicy;
    }

    /**
     * 兼容旧调用方的完整病害视图；索引流程应使用 {@link #convertAll(DiseaseKnowledgeRepository.DiseaseRow)}。
     */
    public KnowledgeDocument convert(DiseaseKnowledgeRepository.DiseaseRow row) {
        List<String> aliases = split(row.aliases());
        String content = """
                植物：%s
                病害：%s
                别名：%s
                常见症状：%s
                视觉特征：%s
                诱发条件：%s
                环境条件：%s
                处理方法：%s
                预防方法：%s
                知识来源：%s
                """.formatted(safe(row.plantName()), safe(row.diseaseName()), String.join("、", aliases),
                safe(row.symptoms()), safe(row.visualSymptoms()), safe(row.triggerConditions()),
                safe(row.environmentConditions()), safe(row.treatment()), safe(row.prevention()), safe(row.source()));
        String documentId = id(row.id());
        String fragmentId = "DISEASE:" + row.id() + ":完整资料:0";
        return new KnowledgeDocument(documentId, KnowledgeSource.DISEASE, row.id(),
                safe(row.plantName()) + safe(row.diseaseName()) + "知识",
                content, safe(row.canonicalPlantId()), safe(row.plantName()), "DISEASE_KNOWLEDGE",
                combineTags(row.diseaseName(), aliases), trust(row.sourceLevel()), false,
                0, 0, 0, 0, sourceUpdatedAt(row), fragmentMetadata(row,
                logicalEvidenceId(row.id(), "完整资料"), fragmentId, 0, 1, 0, 1, "完整资料", content));
    }

    public List<KnowledgeDocument> convertAll(DiseaseKnowledgeRepository.DiseaseRow row) {
        List<String> aliases = split(row.aliases());
        String prefix = """
                植物：%s
                病害：%s
                别名：%s

                """.formatted(safe(row.plantName()), safe(row.diseaseName()), String.join("、", aliases));
        int contentBudget = Math.max(1, chunkPolicy.maxTokens(KnowledgeSource.DISEASE)
                - TokenAwareTextChunker.countTokens(prefix));
        List<DiseaseChunk> chunks = new ArrayList<>();
        addSection(chunks, "症状", joinFields("常见症状", row.symptoms(), "视觉特征", row.visualSymptoms()), contentBudget);
        addSection(chunks, "诱因", joinFields("诱发条件", row.triggerConditions(), "环境条件", row.environmentConditions()), contentBudget);
        addSection(chunks, "处理", joinFields("处理方法", row.treatment()), contentBudget);
        addSection(chunks, "预防", joinFields("预防方法", row.prevention()), contentBudget);
        if (chunks.isEmpty()) {
            chunks.add(new DiseaseChunk("", "病害资料暂无可索引正文。"));
        }

        Map<String, Integer> fragmentCounts = new LinkedHashMap<>();
        for (DiseaseChunk chunk : chunks) {
            fragmentCounts.merge(chunk.section(), 1, Integer::sum);
        }
        Map<String, Integer> fragmentIndexes = new LinkedHashMap<>();
        List<KnowledgeDocument> result = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            DiseaseChunk chunk = chunks.get(index);
            String content = prefix + chunk.content();
            int fragmentIndex = fragmentIndexes.getOrDefault(chunk.section(), 0);
            fragmentIndexes.put(chunk.section(), fragmentIndex + 1);
            String documentId = id(row.id() + ":" + index);
            String fragmentId = logicalEvidenceId(row.id(), sectionName(chunk.section())) + ":" + fragmentIndex;
            result.add(new KnowledgeDocument(
                    documentId, KnowledgeSource.DISEASE, row.id(),
                    safe(row.plantName()) + safe(row.diseaseName()) + "知识", content,
                    safe(row.canonicalPlantId()), safe(row.plantName()), "DISEASE_KNOWLEDGE",
                    combineTags(row.diseaseName(), aliases), trust(row.sourceLevel()), false,
                    0, 0, 0, 0, sourceUpdatedAt(row),
                    fragmentMetadata(row, logicalEvidenceId(row.id(), sectionName(chunk.section())), fragmentId,
                            fragmentIndex, fragmentCounts.get(chunk.section()), index, chunks.size(),
                            chunk.section(), content)
            ));
        }
        return result;
    }

    private void addSection(List<DiseaseChunk> target, String section, String content, int maxTokens) {
        if (content.isBlank()) {
            return;
        }
        for (TokenAwareTextChunker.Chunk chunk : TokenAwareTextChunker.split(content, maxTokens)) {
            target.add(new DiseaseChunk(section, chunk.content()));
        }
    }

    private String joinFields(String... fields) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < fields.length; index += 2) {
            String value = safe(fields[index + 1]);
            if (!value.isBlank()) {
                if (result.length() > 0) {
                    result.append('\n');
                }
                result.append(fields[index]).append('：').append(value);
            }
        }
        return result.toString();
    }

    private List<String> combineTags(String diseaseName, List<String> aliases) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(safe(diseaseName)), aliases.stream())
                .filter(value -> !value.isBlank()).distinct().toList();
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[,，;；|]"))
                .map(String::trim).filter(item -> !item.isBlank()).distinct().toList();
    }

    private double trust(String sourceLevel) {
        if (sourceLevel == null) return 0.8;
        return switch (sourceLevel.trim().toUpperCase(Locale.ROOT)) {
            case "TRUSTED" -> 1.0;
            case "REVIEWED" -> 0.9;
            default -> 0.8;
        };
    }

    private String id(String sourceId) {
        return UUID.nameUUIDFromBytes(("disease:" + sourceId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Map<String, String> fragmentMetadata(DiseaseKnowledgeRepository.DiseaseRow row,
                                                 String logicalEvidenceId, String fragmentId,
                                                 int fragmentIndex, int fragmentCount,
                                                 int chunkIndex, int chunkCount,
                                                 String section, String content) {
        Map<String, String> attributes = attributes(row);
        attributes.put("diseaseId", row.id());
        attributes.put("chunkIndex", String.valueOf(chunkIndex));
        attributes.put("chunkCount", String.valueOf(chunkCount));
        attributes.put("section", section);
        attributes.put("logicalEvidenceId", logicalEvidenceId);
        attributes.put("fragmentId", fragmentId);
        attributes.put("fragmentRole", "CONTENT");
        attributes.put("fragmentIndex", String.valueOf(fragmentIndex));
        attributes.put("fragmentCount", String.valueOf(fragmentCount));
        attributes.put("fragmentSection", section);
        attributes.put("contentHash", contentHash(content));
        attributes.put("sourceUpdatedAt", sourceUpdatedAt(row).equals(Instant.EPOCH)
                ? "" : sourceUpdatedAt(row).toString());
        attributes.put("indexVersion", INDEX_VERSION);
        return attributes;
    }

    private Map<String, String> attributes(DiseaseKnowledgeRepository.DiseaseRow row) {
        Map<String, String> attributes = new LinkedHashMap<>();
        put(attributes, "aliases", row.aliases());
        put(attributes, "visualSymptoms", row.visualSymptoms());
        put(attributes, "triggerConditions", row.triggerConditions());
        put(attributes, "environmentConditions", row.environmentConditions());
        put(attributes, "source", row.source());
        put(attributes, "sourceLevel", row.sourceLevel());
        return attributes;
    }

    private Instant sourceUpdatedAt(DiseaseKnowledgeRepository.DiseaseRow row) {
        if (row.updatedAt() != null) return row.updatedAt();
        if (row.createdAt() != null) return row.createdAt();
        return Instant.EPOCH;
    }

    private String logicalEvidenceId(String diseaseId, String section) {
        return "DISEASE:" + diseaseId + ":" + section;
    }

    private String sectionName(String section) {
        return section == null || section.isBlank() ? "完整资料" : section;
    }

    private String contentHash(String content) {
        return sha256(INDEX_VERSION + "\u0000" + content);
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private record DiseaseChunk(String section, String content) {
    }

    private void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value.trim());
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }
}
