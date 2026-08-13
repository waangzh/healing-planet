package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeSource;
import org.junit.jupiter.api.Test;

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
}
