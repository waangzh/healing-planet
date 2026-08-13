package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SensorConsistencyAnalyzer {

    public Evidence analyze(Evidence diseaseKnowledge, List<Evidence> stateEvidence) {
        Evidence history = stateEvidence.stream()
                .filter(item -> item.type() == EvidenceType.SENSOR_HISTORY).findFirst().orElse(null);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("diseaseEvidenceId", diseaseKnowledge.id());
        if (history == null) {
            metadata.put("status", "UNKNOWN");
            metadata.put("score", 0d);
            return result(diseaseKnowledge, "UNKNOWN", 0d,
                    "没有可用的近7日传感器数据，无法校验视觉候选与环境诱因是否一致。", metadata, null);
        }

        String conditions = conditionText(diseaseKnowledge).toLowerCase(Locale.ROOT);
        Double soil = number(history.metadata(), "last7dAverage.soilMoisture");
        Double humidity = number(history.metadata(), "last7dAverage.humidity");
        Double temperature = number(history.metadata(), "last7dAverage.temperature");
        put(metadata, "soilMoisture7dAverage", soil);
        put(metadata, "humidity7dAverage", humidity);
        put(metadata, "temperature7dAverage", temperature);

        Match match = evaluate(conditions, soil, humidity, temperature);
        metadata.put("status", match.status());
        metadata.put("score", match.score());
        return result(diseaseKnowledge, match.status(), match.score(), match.reason(), metadata, history.timestamp());
    }

    private Match evaluate(String conditions, Double soil, Double humidity, Double temperature) {
        if (conditions.isBlank()) return new Match("UNKNOWN", 0d, "病害知识未提供可校验的环境诱因。");
        boolean wet = containsAny(conditions, "积水", "高湿", "过湿", "湿度过高", "poor drainage", "waterlog");
        boolean dry = containsAny(conditions, "干燥", "缺水", "低湿", "drought", "dry condition");
        boolean hot = containsAny(conditions, "高温", "炎热", "high temperature", "heat");
        boolean cold = containsAny(conditions, "低温", "寒冷", "low temperature", "cold");
        if (!(wet || dry || hot || cold)) return new Match("UNKNOWN", 0d,
                "当前只支持对湿度与温度诱因做确定性校验，该知识条件无法直接映射。");

        if (wet && highMoisture(soil, humidity)) return new Match("SUPPORT", 1d,
                "病害知识提到过湿或积水诱因，近7日平均湿度数据与之一致。");
        if (wet && lowMoisture(soil, humidity)) return new Match("CONFLICT", -1d,
                "病害知识提到过湿或积水诱因，但近7日数据显示偏干，当前环境记录不支持该诱因。");
        if (dry && lowMoisture(soil, humidity)) return new Match("SUPPORT", 1d,
                "病害知识提到干燥或缺水诱因，近7日平均湿度数据与之一致。");
        if (dry && highMoisture(soil, humidity)) return new Match("CONFLICT", -1d,
                "病害知识提到干燥或缺水诱因，但近7日数据显示偏湿。");
        if (hot && temperature != null && temperature >= 30) return new Match("SUPPORT", 1d,
                "病害知识提到高温诱因，近7日平均温度达到高温校验条件。");
        if (hot && temperature != null && temperature <= 20) return new Match("CONFLICT", -1d,
                "病害知识提到高温诱因，但近7日平均温度偏低。");
        if (cold && temperature != null && temperature <= 15) return new Match("SUPPORT", 1d,
                "病害知识提到低温诱因，近7日平均温度达到低温校验条件。");
        if (cold && temperature != null && temperature >= 25) return new Match("CONFLICT", -1d,
                "病害知识提到低温诱因，但近7日平均温度偏高。");
        return new Match("INCONCLUSIVE", 0d, "传感器有数据，但未达到明确支持或冲突的确定性阈值。");
    }

    private Evidence result(Evidence knowledge, String status, double score, String reason,
                            Map<String, Object> metadata, Instant timestamp) {
        String content = "一致性结论：%s\n%s".formatted(status, reason);
        return new Evidence("consistency:" + knowledge.id(), EvidenceType.SENSOR_CONSISTENCY,
                knowledge.sourceId(), "SENSOR_CONSISTENCY", "视觉候选与环境一致性", content,
                null, null, 0.95, score, metadata, timestamp);
    }

    private String conditionText(Evidence knowledge) {
        return String.valueOf(knowledge.metadata().getOrDefault("triggerConditions", "")) + " "
                + String.valueOf(knowledge.metadata().getOrDefault("environmentConditions", ""));
    }

    private boolean highMoisture(Double soil, Double humidity) { return soil != null && soil >= 75 || humidity != null && humidity >= 80; }
    private boolean lowMoisture(Double soil, Double humidity) { return soil != null && soil <= 30 || humidity != null && humidity <= 35; }
    private boolean containsAny(String text, String... terms) { return java.util.Arrays.stream(terms).anyMatch(text::contains); }
    private Double number(Map<String, Object> map, String key) { Object v = map.get(key); return v instanceof Number n ? n.doubleValue() : null; }
    private void put(Map<String, Object> map, String key, Object value) { if (value != null) map.put(key, value); }
    private record Match(String status, double score, String reason) { }
}
