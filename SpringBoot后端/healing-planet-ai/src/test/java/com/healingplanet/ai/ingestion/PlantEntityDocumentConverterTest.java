package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlantEntityDocumentConverterTest {

    private final PlantEntityDocumentConverter converter = new PlantEntityDocumentConverter();

    @Test
    void shouldCreateOneCompactEntityDocumentPerPlant() {
        var document = converter.convert(new KnowledgeRepository.PlantEntityRow(
                "1", "Epipremnum aureum", "绿萝"));

        assertThat(document.source()).isEqualTo(KnowledgeSource.PLANT_ENTITY);
        assertThat(document.canonicalPlantId()).isEqualTo("1");
        assertThat(document.content()).isEqualTo("植物名称：绿萝\n学名：Epipremnum aureum");
        assertThat(document.metadata()).containsEntry("entityType", "PLANT")
                .containsEntry("scientificName", "Epipremnum aureum");
    }
}
