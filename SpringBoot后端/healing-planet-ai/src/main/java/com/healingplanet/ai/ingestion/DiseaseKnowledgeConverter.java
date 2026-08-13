package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class DiseaseKnowledgeConverter {

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
        Map<String, String> attributes = new LinkedHashMap<>();
        put(attributes, "aliases", row.aliases());
        put(attributes, "visualSymptoms", row.visualSymptoms());
        put(attributes, "triggerConditions", row.triggerConditions());
        put(attributes, "environmentConditions", row.environmentConditions());
        put(attributes, "source", row.source());
        put(attributes, "sourceLevel", row.sourceLevel());
        return new KnowledgeDocument(id(row.id()), KnowledgeSource.DISEASE, row.id(),
                safe(row.plantName()) + safe(row.diseaseName()) + "知识",
                content, safe(row.canonicalPlantId()), safe(row.plantName()), "DISEASE_KNOWLEDGE",
                combineTags(row.diseaseName(), aliases), trust(row.sourceLevel()), false,
                0, 0, 0, 0, Instant.EPOCH, attributes);
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

    private void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value.trim());
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }
}
