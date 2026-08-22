package com.healingplanet.ai.retrieval;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Maps user vocabulary to the stable knowledge-topic taxonomy used by the
 * retriever. Rules are grouped by concept so routing and entity parsing share
 * one interpretation of care-related phrases.
 */
final class KnowledgeTopicClassifier {
    private static final List<TopicRule> RULES = List.of(
            new TopicRule("LIGHT", Pattern.compile(
                    "光照|日照|采光|光线|阳光|太阳|明亮|阴暗|背阴|遮阴|散射光?|直射光?|弱光|强光|烈日|暴晒|曝晒|晒")),
            new TopicRule("WATERING", Pattern.compile(
                    "浇水?|补水|灌溉|积水|水涝|涝害|缺水|控水|断水|干透|偏干|见干见湿")),
            new TopicRule("TEMPERATURE", Pattern.compile(
                    "温度|气温|高温|低温|炎热|寒冷|冷凉|温暖|耐寒|耐冷|耐热|冻伤")),
            new TopicRule("HUMIDITY", Pattern.compile(
                    "湿度|潮湿|干燥|湿润|干爽|湿热|加湿|保湿")),
            new TopicRule("FERTILIZING", Pattern.compile(
                    "施肥|肥料|追肥|底肥|基肥|肥效|薄肥")),
            new TopicRule("GENERAL_CARE", Pattern.compile(
                    "土壤|盆土|介质|基质|通风|透气|换盆|修剪|株型|根系"))
    );

    private KnowledgeTopicClassifier() {
    }

    static Set<String> classify(String query) {
        String text = normalize(query);
        Set<String> topics = new LinkedHashSet<>();
        for (TopicRule rule : RULES) {
            if (rule.pattern().matcher(text).find()) topics.add(rule.knowledgeType());
        }
        return Collections.unmodifiableSet(topics);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private record TopicRule(String knowledgeType, Pattern pattern) {
    }
}
