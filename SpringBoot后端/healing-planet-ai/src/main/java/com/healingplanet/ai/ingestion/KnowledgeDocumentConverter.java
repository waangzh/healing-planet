package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class KnowledgeDocumentConverter {

    public List<KnowledgeDocument> fromPlant(KnowledgeRepository.PlantRow plant) {
        List<KnowledgeDocument> documents = new ArrayList<>();
        addPlantTopic(documents, plant, "LIGHT", "光照", plant.lightRequirements());
        addPlantTopic(documents, plant, "WATERING", "浇水", plant.wateringFrequency());
        addPlantTopic(documents, plant, "TEMPERATURE", "温度", plant.temperaturePreference());
        addPlantTopic(documents, plant, "HUMIDITY", "湿度", plant.humidityPreference());
        addPlantTopic(documents, plant, "FERTILIZING", "施肥", plant.fertilizingTips());
        for (String chunk : semanticChunks(plant.detailAdvice(), 900)) {
            addPlantTopic(documents, plant, "GENERAL_CARE", "综合养护", chunk);
        }
        return documents;
    }

    public List<KnowledgeDocument> fromPost(KnowledgeRepository.PostRow post) {
        List<String> tags = post.tags() == null || post.tags().isBlank()
                ? List.of()
                : Arrays.stream(post.tags().split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        List<String> chunks = semanticChunks(post.content(), 1200);
        if (chunks.isEmpty()) chunks = List.of("");
        List<KnowledgeDocument> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String content = "标题：%s\n标签：%s\n\n%s".formatted(
                    safe(post.title()), String.join("、", tags), chunks.get(i));
            result.add(new KnowledgeDocument(
                    id("post", post.id() + ":" + i), KnowledgeSource.COMMUNITY, post.id(), safe(post.title()), content,
                    "", inferPlantName(tags), "COMMUNITY_EXPERIENCE", tags,
                    post.essence() ? 0.75 : 0.5, post.essence(), post.likes(), post.collects(),
                    post.comments(), post.views(), post.createdAt(), java.util.Map.of()
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
        if (value == null) return "";
        return value.replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("[`*_>|~-]", " ")
                .replaceAll("[ \\t]+", " ")
                .trim();
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
}
