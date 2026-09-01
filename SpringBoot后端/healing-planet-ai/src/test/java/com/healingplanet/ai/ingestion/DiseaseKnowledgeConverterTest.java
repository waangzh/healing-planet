package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DiseaseKnowledgeConverterTest {
    @Test
    void shouldKeepDiseaseConditionsAndSourceAsEvidenceMetadata() {
        var row = new DiseaseKnowledgeRepository.DiseaseRow("d1", "plant-1", "绿萝", "根腐病",
                "烂根,Root Rot", "黄叶、萎蔰", "根部发黑", "长期积水",
                "过湿且排水不畅", "剪除腐根", "改善排水", "农技推广资料", "TRUSTED");

        var document = new DiseaseKnowledgeConverter().convert(row);

        assertThat(document.source()).isEqualTo(KnowledgeSource.DISEASE);
        assertThat(document.tags()).contains("根腐病", "烂根", "Root Rot");
        assertThat(document.metadata()).containsEntry("triggerConditions", "长期积水")
                .containsEntry("source", "农技推广资料");
        assertThat(document.content()).contains("处理方法：剪除腐根");
    }

    @Test
    void shouldIndexDiseaseSectionsAsRelatedTokenBoundedChunks() {
        var row = new DiseaseKnowledgeRepository.DiseaseRow("d2", "plant-1", "绿萝", "根腐病",
                "烂根,Root Rot", "黄叶、萎蔰", "根部发黑", "长期积水",
                "过湿且排水不畅", "剪除腐根", "改善排水", "农技推广资料", "TRUSTED",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"));

        var documents = new DiseaseKnowledgeConverter().convertAll(row);

        assertThat(documents).hasSize(4).allSatisfy(document -> {
            assertThat(document.sourceId()).isEqualTo("d2");
            assertThat(TokenAwareTextChunker.countTokens(document.embeddingText()))
                    .isLessThanOrEqualTo(new ChunkPolicy(new RagProperties()).maxTokens(KnowledgeSource.DISEASE));
            assertThat(document.metadata()).containsEntry("diseaseId", "d2")
                    .containsKeys("chunkIndex", "chunkCount", "section", "contentHash", "sourceUpdatedAt", "indexVersion",
                            "logicalEvidenceId", "fragmentId", "fragmentRole", "fragmentIndex", "fragmentCount",
                            "fragmentSection");
            assertThat(document.metadata().get("chunkCount")).isEqualTo("4");
            assertThat(document.metadata().get("sourceUpdatedAt")).isEqualTo("2026-02-01T00:00:00Z");
        });
        assertThat(documents).extracting(document -> document.metadata().get("section"))
                .containsExactly("症状", "诱因", "处理", "预防");
        assertThat(documents).extracting(document -> document.metadata().get("logicalEvidenceId"))
                .doesNotHaveDuplicates();
        assertThat(documents).allSatisfy(document -> assertThat(document.metadata().get("logicalEvidenceId"))
                .isEqualTo("DISEASE:d2:" + document.metadata().get("section")));
    }
}
