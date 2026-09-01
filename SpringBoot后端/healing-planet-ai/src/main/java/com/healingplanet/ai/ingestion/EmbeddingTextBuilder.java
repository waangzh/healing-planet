package com.healingplanet.ai.ingestion;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 统一构造进入 embedding / BM25 / reranker 的语义文本。
 *
 * <p>互动数、精选标记、可信度和时效等排序信号不在这里出现，它们继续只由
 * SourceAwareRanker 使用。</p>
 */
@Component
public class EmbeddingTextBuilder {

    public String plant(String commonName, String scientificName, String topic, String body) {
        return plantPrefix(commonName, scientificName, topic) + safe(body);
    }

    public String plantPrefix(String commonName, String scientificName, String topic) {
        return "植物：%s\n学名：%s\n养护主题：%s\n\n".formatted(
                safe(commonName), safe(scientificName), safe(topic));
    }

    public String community(String title, List<String> tags, String plantName, String body) {
        return communityPrefix(title, tags, plantName) + safe(body);
    }

    public String communityPrefix(String title, List<String> tags, String plantName) {
        StringBuilder prefix = new StringBuilder("标题：%s\n标签：%s".formatted(
                safe(title), String.join("、", tags == null ? List.of() : tags)));
        if (!safe(plantName).isBlank()) {
            prefix.append("\n植物：").append(safe(plantName));
        }
        return prefix.append("\n\n").toString();
    }

    public String disease(String plantName, String diseaseName, List<String> aliases, String body) {
        return diseasePrefix(plantName, diseaseName, aliases) + safe(body);
    }

    public String diseasePrefix(String plantName, String diseaseName, List<String> aliases) {
        return "植物：%s\n病害：%s\n别名：%s\n\n".formatted(
                safe(plantName), safe(diseaseName), String.join("、", aliases == null ? List.of() : aliases));
    }

    public String plantEntity(String commonName, String scientificName, List<String> aliases) {
        return aliases == null || aliases.isEmpty()
                ? "植物名称：%s\n学名：%s".formatted(safe(commonName), safe(scientificName))
                : "植物名称：%s\n学名：%s\n别名：%s".formatted(
                        safe(commonName), safe(scientificName), String.join("、", aliases));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
