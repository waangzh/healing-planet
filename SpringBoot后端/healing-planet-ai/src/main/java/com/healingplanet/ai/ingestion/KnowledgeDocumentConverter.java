package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
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
    private static final String INDEX_VERSION = "chunk-v2";

    public List<KnowledgeDocument> fromPlant(KnowledgeRepository.PlantRow plant) {
        List<KnowledgeDocument> documents = new ArrayList<>();
        addPlantTopic(documents, plant, "LIGHT", "光照", plant.lightRequirements());
        addPlantTopic(documents, plant, "WATERING", "浇水", plant.wateringFrequency());
        addPlantTopic(documents, plant, "TEMPERATURE", "温度", plant.temperaturePreference());
        addPlantTopic(documents, plant, "HUMIDITY", "湿度", plant.humidityPreference());
        addPlantTopic(documents, plant, "FERTILIZING", "施肥", plant.fertilizingTips());
        for (String chunk : semanticChunks(plant.detailAdvice(), 300)) {
            addPlantTopic(documents, plant, "GENERAL_CARE", "综合养护", chunk);
        }
        return documents;
    }

    public List<KnowledgeDocument> fromPost(KnowledgeRepository.PostRow post) {
        List<String> tags = post.tags() == null || post.tags().isBlank()
                ? List.of()
                : Arrays.stream(post.tags().split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        String contentPrefix = "标题：%s\n标签：%s\n\n".formatted(safe(post.title()), String.join("、", tags));
        int contentBudget = Math.max(1, TokenAwareTextChunker.COMMUNITY_MAX_TOKENS
                - TokenAwareTextChunker.countTokens(contentPrefix));
        List<TokenAwareTextChunker.Chunk> chunks = TokenAwareTextChunker.split(post.content(), contentBudget);
        if (chunks.isEmpty()) chunks = List.of(new TokenAwareTextChunker.Chunk("", ""));
        List<KnowledgeDocument> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TokenAwareTextChunker.Chunk chunk = chunks.get(i);
            String content = contentPrefix + chunk.content();
            result.add(new KnowledgeDocument(
                    id("post", post.id() + ":" + i), KnowledgeSource.COMMUNITY, post.id(), safe(post.title()), content,
                    "", inferPlantName(tags), "COMMUNITY_EXPERIENCE", tags,
                    post.essence() ? 0.75 : 0.5, post.essence(), post.likes(), post.collects(),
                    post.comments(), post.views(), post.createdAt(), chunkMetadata(i, chunks.size(), chunk.section(),
                    content, sourceUpdatedAt(post.updatedAt(), post.createdAt()))
            ));
        }
        return result;
    }

    private void addPlantTopic(List<KnowledgeDocument> target, KnowledgeRepository.PlantRow plant,
                               String type, String topic, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String title = safe(plant.commonName()) + topic + "指南";
        String content = "植物：%s\n学名：%s\n养护主题：%s\n\n%s".formatted(
                safe(plant.commonName()), safe(plant.scientificName()), topic, value.trim());
        target.add(new KnowledgeDocument(
                id("plant", plant.id() + ":" + type + ":" + target.size()), KnowledgeSource.PLANT,
                plant.id(), title, content, plant.id(), safe(plant.commonName()), type, List.of(topic),
                1.0, false, 0, 0, 0, 0, Instant.EPOCH, java.util.Map.of()
        ));
    }

    static List<String> semanticChunks(String text, int maxCharacters) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String cleaned = stripMarkdown(text);
        String[] paragraphs = cleaned.split("(?:\\r?\\n){2,}");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String value = paragraph.trim();
            if (value.isBlank()) continue;
            if (current.length() > 0 && current.length() + value.length() + 2 > maxCharacters) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (value.length() > maxCharacters) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                for (int start = 0; start < value.length(); start += maxCharacters) {
                    chunks.add(value.substring(start, Math.min(value.length(), start + maxCharacters)));
                }
            } else {
                if (current.length() > 0) current.append("\n\n");
                current.append(value);
            }
        }
        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }

    static String stripMarkdown(String value) {
        return MarkdownPlainTextSanitizer.strip(value);
    }

    private String inferPlantName(List<String> tags) {
        return tags.isEmpty() ? "" : tags.get(0);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String id(String namespace, String source) {
        return UUID.nameUUIDFromBytes((namespace + ":" + source).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Map<String, String> chunkMetadata(int chunkIndex, int chunkCount, String section,
                                              String content, Instant sourceUpdatedAt) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("chunkIndex", String.valueOf(chunkIndex));
        result.put("chunkCount", String.valueOf(chunkCount));
        result.put("section", safe(section));
        result.put("contentHash", sha256(content));
        result.put("sourceUpdatedAt", sourceUpdatedAt == null ? "" : sourceUpdatedAt.toString());
        result.put("indexVersion", INDEX_VERSION);
        return result;
    }

    private Instant sourceUpdatedAt(Instant updatedAt, Instant createdAt) {
        return updatedAt == null ? createdAt : updatedAt;
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }
}
