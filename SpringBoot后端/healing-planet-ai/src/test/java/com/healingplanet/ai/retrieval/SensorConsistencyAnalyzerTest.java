package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensorConsistencyAnalyzerTest {
    private final SensorConsistencyAnalyzer analyzer = new SensorConsistencyAnalyzer();

    @Test
    void shouldSupportWetTriggeredDiseaseWhenHistoryIsWet() {
        Evidence knowledge = evidence(EvidenceType.DISEASE_KNOWLEDGE,
                Map.of("triggerConditions", "长期积水和过湿"));
        Evidence history = evidence(EvidenceType.SENSOR_HISTORY,
                Map.of("last7dAverage.soilMoisture", 82d));

        Evidence result = analyzer.analyze(knowledge, List.of(history));

        assertThat(result.type()).isEqualTo(EvidenceType.SENSOR_CONSISTENCY);
        assertThat(result.metadata()).containsEntry("status", "SUPPORT");
        assertThat(result.content()).contains("一致");
    }

    @Test
    void shouldMarkUnknownWhenSensorHistoryIsMissing() {
        Evidence result = analyzer.analyze(evidence(EvidenceType.DISEASE_KNOWLEDGE,
                Map.of("triggerConditions", "高湿")), List.of());

        assertThat(result.metadata()).containsEntry("status", "UNKNOWN");
        assertThat(result.content()).contains("无法校验");
    }

    private Evidence evidence(EvidenceType type, Map<String, Object> metadata) {
        return new Evidence(type.name(), type, "source", type.name(), "title", "content",
                0.8, null, 1.0, 0.9, metadata, Instant.EPOCH);
    }
}
