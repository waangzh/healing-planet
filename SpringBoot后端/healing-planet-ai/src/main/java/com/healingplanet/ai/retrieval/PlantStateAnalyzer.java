package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.PlantState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PlantStateAnalyzer {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final Clock clock;

    public PlantStateAnalyzer(@Qualifier("ragClock") Clock clock) {
        this.clock = clock;
    }

    public List<Evidence> analyze(PlantState state) {
        Instant observedAt = state.observedAt() == null ? null : state.observedAt().atZone(BUSINESS_ZONE).toInstant();
        Map<String, Object> common = new LinkedHashMap<>();
        put(common, "plantInstanceId", state.plantInstanceId());
        put(common, "plantId", state.plantId());
        put(common, "plantName", state.plantName());
        put(common, "deviceId", state.deviceId());
        put(common, "observedAt", observedAt);
        if (observedAt != null) common.put("stale", Duration.between(observedAt, clock.instant()).toMinutes() > 30);

        List<String> violations = violations(state.current(), state.thresholds());
        Map<String, Object> liveMetadata = new LinkedHashMap<>(common);
        liveMetadata.put("thresholdViolations", violations);
        Evidence live = evidence("state:live:" + state.plantInstanceId(), EvidenceType.LIVE_STATE,
                "植物当前状态", liveContent(state, violations), liveMetadata, observedAt, 1.0);

        Map<String, Object> historyMetadata = new LinkedHashMap<>(common);
        historyMetadata.put("last24hSamples", samples(state.last24h()));
        historyMetadata.put("last7dSamples", samples(state.last7d()));
        putMetrics(historyMetadata, "current", state.current());
        putMetrics(historyMetadata, "last24hAverage", state.last24h() == null ? null : state.last24h().average());
        putMetrics(historyMetadata, "last7dAverage", state.last7d() == null ? null : state.last7d().average());
        Evidence history = evidence("state:history:" + state.plantInstanceId(), EvidenceType.SENSOR_HISTORY,
                "植物环境趋势", historyContent(state), historyMetadata, observedAt, 0.95);
        return List.of(live, history);
    }

    private Evidence evidence(String id, EvidenceType type, String title, String content,
                              Map<String, Object> metadata, Instant timestamp, double trust) {
        return new Evidence(id, type, "plant-instance:" + metadata.get("plantInstanceId"),
                "SMART_GREEN_PLANT", title, content, 1d, null, trust, trust, metadata, timestamp);
    }

    private String liveContent(PlantState state, List<String> violations) {
        PlantState.SensorMetrics current = state.current();
        String freshness = state.observedAt() == null ? "未知" : state.observedAt().toString();
        String freshnessStatus = isStale(state.observedAt()) ? "已超过30分钟，不能视为实时读数" : "30分钟内";
        return "植物：%s\n数据采集时间：%s\n数据时效：%s\n当前温度：%s ℃\n当前空气湿度：%s %%\n当前土壤湿度：%s %%\n当前光照：%s Lux\n当前 CO₂：%s ppm\n阈值判断：%s"
                .formatted(blank(state.plantName()), freshness, freshnessStatus,
                        value(current == null ? null : current.temperature()),
                        value(current == null ? null : current.humidity()),
                        value(current == null ? null : current.soilMoisture()),
                        value(current == null ? null : current.lightIntensity()),
                        value(current == null ? null : current.co2()),
                        violations.isEmpty() ? "当前读数均未超出已配置阈值" : String.join("；", violations));
    }

    private boolean isStale(java.time.LocalDateTime observedAt) {
        if (observedAt == null) return true;
        return Duration.between(observedAt.atZone(BUSINESS_ZONE).toInstant(), clock.instant()).toMinutes() > 30;
    }

    private String historyContent(PlantState state) {
        return window("过去24小时", state.last24h()) + "\n" + window("过去7天", state.last7d());
    }

    private String window(String label, PlantState.SensorWindow window) {
        if (window == null || samples(window) == 0) return label + "：无有效传感器样本";
        PlantState.SensorMetrics avg = window.average();
        PlantState.SensorTrends trends = window.trends();
        return "%s（%d 个样本）：平均温度 %s ℃，平均空气湿度 %s %%，平均土壤湿度 %s %%，平均光照 %s Lux，平均 CO₂ %s ppm；趋势：温度%s、空气湿度%s、土壤湿度%s、光照%s、CO₂%s"
                .formatted(label, samples(window), value(avg == null ? null : avg.temperature()),
                        value(avg == null ? null : avg.humidity()), value(avg == null ? null : avg.soilMoisture()),
                        value(avg == null ? null : avg.lightIntensity()), value(avg == null ? null : avg.co2()),
                        trend(trends == null ? null : trends.temperature()), trend(trends == null ? null : trends.humidity()),
                        trend(trends == null ? null : trends.soilMoisture()), trend(trends == null ? null : trends.lightIntensity()),
                        trend(trends == null ? null : trends.co2()));
    }

    private List<String> violations(PlantState.SensorMetrics current, PlantState.SensorThresholds threshold) {
        if (current == null || threshold == null) return List.of();
        List<String> result = new ArrayList<>();
        violation(result, "温度", current.temperature(), threshold.temperatureMin(), threshold.temperatureMax(), "℃");
        violation(result, "空气湿度", current.humidity(), threshold.humidityMin(), threshold.humidityMax(), "%");
        violation(result, "土壤湿度", current.soilMoisture(), threshold.soilMoistureMin(), threshold.soilMoistureMax(), "%");
        violation(result, "光照", current.lightIntensity(), threshold.lightIntensityMin(), threshold.lightIntensityMax(), "Lux");
        violation(result, "CO₂", current.co2(), threshold.co2Min(), threshold.co2Max(), "ppm");
        return List.copyOf(result);
    }

    private void violation(List<String> result, String name, Double value, Double min, Double max, String unit) {
        if (value == null) return;
        if (min != null && value < min) result.add(name + "低于阈值（" + value(value) + unit + " < " + value(min) + unit + "）");
        else if (max != null && value > max) result.add(name + "高于阈值（" + value(value) + unit + " > " + value(max) + unit + "）");
    }

    private int samples(PlantState.SensorWindow value) {
        return value == null || value.sampleCount() == null ? 0 : value.sampleCount();
    }

    private String trend(String value) {
        if ("INCREASING".equals(value)) return "上升";
        if ("DECREASING".equals(value)) return "下降";
        if ("STABLE".equals(value)) return "稳定";
        return "未知";
    }

    private String value(Double value) { return value == null ? "无数据" : String.format(Locale.ROOT, "%.2f", value); }
    private String blank(String value) { return value == null || value.isBlank() ? "未知" : value; }
    private void put(Map<String, Object> map, String key, Object value) { if (value != null) map.put(key, value); }
    private void putMetrics(Map<String, Object> map, String prefix, PlantState.SensorMetrics metrics) {
        if (metrics == null) return;
        put(map, prefix + ".temperature", metrics.temperature());
        put(map, prefix + ".humidity", metrics.humidity());
        put(map, prefix + ".soilMoisture", metrics.soilMoisture());
        put(map, prefix + ".lightIntensity", metrics.lightIntensity());
        put(map, prefix + ".co2", metrics.co2());
    }
}
