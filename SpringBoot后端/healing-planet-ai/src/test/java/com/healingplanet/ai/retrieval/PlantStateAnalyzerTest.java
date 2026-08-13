package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.PlantState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PlantStateAnalyzerTest {
    @Test
    void shouldCreateLiveAndHistoryEvidenceWithThresholdViolation() {
        var current = new PlantState.SensorMetrics(28d, 50d, 20d, 5000d, 420d);
        var trends = new PlantState.SensorTrends("STABLE", "STABLE", "DECREASING", "STABLE", "STABLE");
        var window = new PlantState.SensorWindow(12, current, current, current, trends);
        var thresholds = new PlantState.SensorThresholds(18d, 30d, 40d, 70d,
                30d, 70d, 1000d, 10000d, 300d, 1000d);
        var state = new PlantState(102L, "3", "绿萝", 7L, LocalDateTime.now(),
                current, window, window, thresholds);

        var evidence = new PlantStateAnalyzer().analyze(state);

        assertThat(evidence).extracting(item -> item.type())
                .containsExactly(EvidenceType.LIVE_STATE, EvidenceType.SENSOR_HISTORY);
        assertThat(evidence.get(0).content()).contains("土壤湿度低于阈值", "数据采集时间");
        assertThat(evidence.get(1).content()).contains("过去24小时", "土壤湿度下降");
    }
}
