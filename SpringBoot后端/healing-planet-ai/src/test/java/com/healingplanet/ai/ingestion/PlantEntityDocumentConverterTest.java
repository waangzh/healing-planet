package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlantEntityDocumentConverterTest {

    private final PlantEntityDocumentConverter converter = new PlantEntityDocumentConverter();

    @Test
    void shouldCreateOneCompactEntityDocumentPerPlant() {
        var document = converter.convert(new KnowledgeRepository.PlantEntityRow(
                "1", "Epipremnum aureum", "绿萝", List.of("黄金葛", "绿箩")));

        assertThat(document.source()).isEqualTo(KnowledgeSource.PLANT_ENTITY);
        assertThat(document.canonicalPlantId()).isEqualTo("1");
        assertThat(document.content()).isEqualTo("植物名称：绿萝\n学名：Epipremnum aureum\n别名：黄金葛、绿箩");
        assertThat(document.metadata()).containsEntry("entityType", "PLANT")
                .containsEntry("scientificName", "Epipremnum aureum")
                .containsEntry("aliases", "黄金葛,绿箩");
    }
}
