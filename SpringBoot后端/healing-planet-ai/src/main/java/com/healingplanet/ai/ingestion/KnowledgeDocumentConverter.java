package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.retrieval.PlantCatalogIndex;
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
import java.util.Map;
import java.util.UUID;

@Component
public class KnowledgeDocumentConverter {
    private static final String INDEX_VERSION = "logical-evidence-v2";
    private final PlantCatalogIndex plantCatalogIndex;
    private final ChunkPolicy chunkPolicy;

    /** Kept for focused converter tests that do not need a catalog snapshot. */
    KnowledgeDocumentConverter() {
        this(null, new ChunkPolicy(new RagProperties()));
    }

    public KnowledgeDocumentConverter(PlantCatalogIndex plantCatalogIndex) {
        this(plantCatalogIndex, new ChunkPolicy(new RagProperties()));
    }

    @Autowired
    public KnowledgeDocumentConverter(PlantCatalogIndex plantCatalogIndex, ChunkPolicy chunkPolicy) {
        this.plantCatalogIndex = plantCatalogIndex;
        this.chunkPolicy = chunkPolicy;
    }

    public List<KnowledgeDocument> fromPlant(KnowledgeRepository.PlantRow plant) {
        List<KnowledgeDocument> documents = new ArrayList<>();
        addPlantTopic(documents, plant, "LIGHT", "光照", plant.lightRequirements());
        addPlantTopic(documents, plant, "WATERING", "浇水", plant.wateringFrequency());
        addPlantTopic(documents, plant, "TEMPERATURE", "温度", plant.temperaturePreference());
        addPlantTopic(documents, plant, "HUMIDITY", "湿度", plant.humidityPreference());
        addPlantTopic(documents, plant, "FERTILIZING", "施肥", plant.fertilizingTips());
        addPlantTopic(documents, plant, "GENERAL_CARE", "综合养护",
                TokenAwareTextChunker.split(plant.detailAdvice(), Math.max(1,
                                chunkPolicy.maxTokens(KnowledgeSource.PLANT)
                                        - TokenAwareTextChunker.countTokens(plantPrefix(plant, "综合养护"))))
                        .stream().map(chunk -> new PlantChunk(chunk.content(),
                                chunk.section().isBlank() ? "综合养护" : chunk.section())).toList());
        return documents;
    }

    public List<KnowledgeDocument> fromPost(KnowledgeRepository.PostRow post) {
        List<String> tags = post.tags() == null || post.tags().isBlank()
                ? List.of()
                : Arrays.stream(post.tags().split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        PlantCatalogIndex.CommunityPlantResolution plantResolution = plantCatalogIndex == null
                ? PlantCatalogIndex.CommunityPlantResolution.empty()
                : plantCatalogIndex.resolveCommunityPlants(post.title(), post.content(), tags);
        String canonicalPlantId = plantResolution.resolvedPlantIds().size() == 1
                ? plantResolution.resolvedPlantIds().get(0) : "";
        String plantName = plantResolution.primaryPlantName().isBlank()
                ? inferPlantName(tags) : plantResolution.primaryPlantName();
        String contentPrefix = "标题：%s\n标签：%s\n\n".formatted(safe(post.title()), String.join("、", tags));
        int contentBudget = Math.max(1, chunkPolicy.maxTokens(KnowledgeSource.COMMUNITY)
                - TokenAwareTextChunker.countTokens(contentPrefix));
        List<TokenAwareTextChunker.Chunk> chunks = TokenAwareTextChunker.split(post.content(), contentBudget);
        if (chunks.isEmpty()) chunks = List.of(new TokenAwareTextChunker.Chunk("", ""));
        List<KnowledgeDocument> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TokenAwareTextChunker.Chunk chunk = chunks.get(i);
            String content = contentPrefix + chunk.content();
            String documentId = id("post", post.id() + ":" + i);
            String fragmentId = "COMMUNITY:" + post.id() + ":" + i;
            Map<String, String> metadata = new LinkedHashMap<>(fragmentMetadata(
                    "COMMUNITY:" + post.id(), fragmentId, i, chunks.size(), chunk.section(), content,
                    sourceUpdatedAt(post.updatedAt(), post.createdAt())));
            metadata.put("resolvedPlantIds", String.join(",", plantResolution.resolvedPlantIds()));
            metadata.put("plantEntityConfidence", Double.toString(plantResolution.confidence()));
            result.add(new KnowledgeDocument(
                    documentId, KnowledgeSource.COMMUNITY, post.id(), safe(post.title()), content,
                    canonicalPlantId, plantName, "COMMUNITY_EXPERIENCE", tags,
                    post.essence() ? 0.75 : 0.5, post.essence(), post.likes(), post.collects(),
                    post.comments(), post.views(), post.createdAt(), metadata
            ));
        }
        return result;
    }

    private void addPlantTopic(List<KnowledgeDocument> target, KnowledgeRepository.PlantRow plant,
                               String type, String topic, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        addPlantTopic(target, plant, type, topic, List.of(new PlantChunk(value.trim(), topic)));
    }

    private void addPlantTopic(List<KnowledgeDocument> target, KnowledgeRepository.PlantRow plant,
                               String type, String topic, List<PlantChunk> fragments) {
        if (fragments.isEmpty()) {
            return;
        }
        String logicalEvidenceId = "PLANT:" + plant.id() + ":" + type;
        String title = safe(plant.commonName()) + topic + "指南";
        for (int index = 0; index < fragments.size(); index++) {
            PlantChunk fragment = fragments.get(index);
            String value = fragment.content();
            String content = plantPrefix(plant, topic) + value.trim();
            String documentId = id("plant", plant.id() + ":" + type + ":" + target.size());
            String fragmentId = logicalEvidenceId + ":" + index;
            target.add(new KnowledgeDocument(
                    documentId, KnowledgeSource.PLANT, plant.id(), title, content,
                    plant.id(), safe(plant.commonName()), type, List.of(topic),
                    1.0, false, 0, 0, 0, 0, Instant.EPOCH,
                    fragmentMetadata(logicalEvidenceId, fragmentId, index, fragments.size(), fragment.section(),
                            content, Instant.EPOCH)
            ));
        }
    }

    private String inferPlantName(List<String> tags) {
        return tags.isEmpty() ? "" : tags.get(0);
    }

    private String plantPrefix(KnowledgeRepository.PlantRow plant, String topic) {
        return "植物：%s\n学名：%s\n养护主题：%s\n\n".formatted(
                safe(plant.commonName()), safe(plant.scientificName()), topic);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String id(String namespace, String source) {
        return UUID.nameUUIDFromBytes((namespace + ":" + source).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Map<String, String> fragmentMetadata(String logicalEvidenceId, String fragmentId,
                                                 int fragmentIndex, int fragmentCount, String section,
                                                 String content, Instant sourceUpdatedAt) {
        Map<String, String> result = new LinkedHashMap<>();
        // chunk 字段保留给旧的检索追踪和离线评测；新字段定义逻辑证据与物理片段的关系。
        result.put("chunkIndex", String.valueOf(fragmentIndex));
        result.put("chunkCount", String.valueOf(fragmentCount));
        result.put("section", safe(section));
        result.put("logicalEvidenceId", logicalEvidenceId);
        result.put("fragmentId", fragmentId);
        result.put("fragmentRole", "CONTENT");
        result.put("fragmentIndex", String.valueOf(fragmentIndex));
        result.put("fragmentCount", String.valueOf(fragmentCount));
        result.put("fragmentSection", safe(section));
        result.put("contentHash", contentHash(content));
        result.put("sourceUpdatedAt", sourceUpdatedAt == null ? "" : sourceUpdatedAt.toString());
        result.put("indexVersion", INDEX_VERSION);
        return result;
    }

    private Instant sourceUpdatedAt(Instant updatedAt, Instant createdAt) {
        return updatedAt == null ? createdAt : updatedAt;
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

    private record PlantChunk(String content, String section) {
    }
}
