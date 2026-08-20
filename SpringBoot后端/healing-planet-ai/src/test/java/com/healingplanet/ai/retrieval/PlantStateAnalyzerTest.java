package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.PlantState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PlantStateAnalyzerTest {
    @Test
    void shouldCreateLiveAndHistoryEvidenceWithThresholdViolation() {
        var current = new PlantState.SensorMetrics(28d, 50d, 20d, 5000d, 420d);
        var trends = new PlantState.SensorTrends("STABLE", "STABLE", "DECREASING", "STABLE", "STABLE");
        var window = new PlantState.SensorWindow(12, current, current, current, trends);
        var thresholds = new PlantState.SensorThresholds(18d, 30d, 40d, 70d,
                30d, 70d, 1000d, 10000d, 300d, 1000d);
        var state = new PlantState(102L, "3", "绿萝", 7L, LocalDateTime.of(2026, 8, 17, 9, 55),
                current, window, window, thresholds);

        var analyzer = new PlantStateAnalyzer(Clock.fixed(Instant.parse("2026-08-17T02:00:00Z"), ZoneOffset.UTC));
        var evidence = analyzer.analyze(state);

        assertThat(evidence).extracting(item -> item.type())
                .containsExactly(EvidenceType.LIVE_STATE, EvidenceType.SENSOR_HISTORY);
        assertThat(evidence.get(0).content()).contains("土壤湿度低于阈值", "数据采集时间");
        assertThat(evidence.get(1).content()).contains("过去24小时", "土壤湿度下降");
        assertThat(evidence.get(0).metadata()).containsEntry("stale", false);
    }

    @Test
    void shouldExposeConfiguredSoilMoistureRangeWhenCurrentValueIsWithinThreshold() {
        var current = new PlantState.SensorMetrics(26d, 55d, 68d, 4500d, 460d);
        var thresholds = new PlantState.SensorThresholds(18d, 30d, 40d, 70d,
                30d, 70d, 1000d, 10000d, 300d, 1000d);
        var state = new PlantState(103L, "1", "绿萝", 8L, LocalDateTime.of(2026, 8, 17, 9, 58),
                current, null, null, thresholds);

        var analyzer = new PlantStateAnalyzer(Clock.fixed(Instant.parse("2026-08-17T02:00:00Z"), ZoneOffset.UTC));
        var evidence = analyzer.analyze(state);

        assertThat(evidence.get(0).content())
                .contains("温度配置范围：18.00℃ - 30.00℃")
                .contains("空气湿度配置范围：40.00% - 70.00%")
                .contains("土壤湿度配置范围：30.00% - 70.00%")
                .contains("光照配置范围：1000.00Lux - 10000.00Lux")
                .contains("CO₂ 配置范围：300.00ppm - 1000.00ppm")
                .contains("当前读数均未超出已配置阈值");
    }

    @Test
    void shouldNotTreatUnconfiguredThresholdsAsNoViolation() {
        var current = new PlantState.SensorMetrics(26d, 50d, 45d, 5000d, 420d);
        var thresholds = new PlantState.SensorThresholds(null, null, null, null,
                null, null, null, null, null, null);
        var state = new PlantState(110L, "1", "绿萝", 15L, LocalDateTime.of(2026, 8, 17, 9, 59),
                current, null, null, thresholds);

        var analyzer = new PlantStateAnalyzer(Clock.fixed(Instant.parse("2026-08-17T02:00:00Z"), ZoneOffset.UTC));
        var evidence = analyzer.analyze(state);

        assertThat(evidence.get(0).content())
                .contains("温度配置范围：未配置", "光照配置范围：未配置")
                .contains("当前状态未配置传感器阈值，无法判断读数是否越界")
                .doesNotContain("当前读数均未超出已配置阈值");
    }

    @Test
    void shouldUseInjectedClockToMarkStaleState() {
        var current = new PlantState.SensorMetrics(28d, 50d, 20d, 5000d, 420d);
        var state = new PlantState(104L, "1", "绿萝", 9L, LocalDateTime.of(2026, 8, 17, 9, 29),
                current, null, null, null);
        var analyzer = new PlantStateAnalyzer(Clock.fixed(Instant.parse("2026-08-17T02:00:00Z"), ZoneOffset.UTC));

        var evidence = analyzer.analyze(state);

        assertThat(evidence.get(0).content()).contains("已超过30分钟，不能视为实时读数");
        assertThat(evidence.get(0).content()).contains("数据距当前：31 分钟");
        assertThat(evidence.get(0).metadata()).containsEntry("stale", true).containsEntry("ageMinutes", 31L);
    }
}
