package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class PlantEntityDocumentConverter {

    public KnowledgeDocument convert(KnowledgeRepository.PlantEntityRow plant) {
        String commonName = safe(plant.commonName());
        String scientificName = safe(plant.scientificName());
        List<String> aliases = plant.aliases().stream()
                .map(this::safe)
                .filter(alias -> !alias.isBlank())
                .distinct()
                .toList();
        String normalizedNames = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(commonName, scientificName), aliases.stream())
                .map(this::normalize).filter(name -> !name.isBlank()).distinct().collect(java.util.stream.Collectors.joining("|"));
        String content = aliases.isEmpty()
                ? "植物名称：%s\n学名：%s".formatted(commonName, scientificName)
                : "植物名称：%s\n学名：%s\n别名：%s".formatted(commonName, scientificName, String.join("、", aliases));
        return new KnowledgeDocument(
                id(plant.id()), KnowledgeSource.PLANT_ENTITY, plant.id(), commonName, content,
                plant.id(), commonName, "PLANT_ENTITY", List.of(), 1.0, false,
                0, 0, 0, 0, Instant.EPOCH,
                Map.of("commonName", commonName, "scientificName", scientificName,
                        "aliases", String.join(",", aliases), "normalizedNames", normalizedNames,
                        "entityType", "PLANT")
        );
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String id(String plantId) {
        return UUID.nameUUIDFromBytes(("plant-entity:" + plantId).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
