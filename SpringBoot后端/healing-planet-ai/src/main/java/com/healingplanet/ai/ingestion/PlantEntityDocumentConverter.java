package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final EmbeddingTextBuilder embeddingTextBuilder;

    public PlantEntityDocumentConverter() {
        this(new EmbeddingTextBuilder());
    }

    @Autowired
    public PlantEntityDocumentConverter(EmbeddingTextBuilder embeddingTextBuilder) {
        this.embeddingTextBuilder = embeddingTextBuilder;
    }

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
        String content = embeddingTextBuilder.plantEntity(commonName, scientificName, aliases);
        return new KnowledgeDocument(
                id(plant.id()), KnowledgeSource.PLANT_ENTITY, plant.id(), commonName, content, content,
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
