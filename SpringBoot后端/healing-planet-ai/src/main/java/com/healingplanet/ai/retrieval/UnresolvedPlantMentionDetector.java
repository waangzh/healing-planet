package com.healingplanet.ai.retrieval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects only an unresolved operand directly coordinated with a registered mention.
 * It does not participate in known-entity linking and intentionally avoids care-topic parsing.
 */
final class UnresolvedPlantMentionDetector {
    private static final String SEPARATORS = "和与跟及、/,，；;";
    private static final List<String> RIGHT_BOUNDARIES = List.of(
            "的", "哪个", "哪种", "谁", "是否", "是不是", "怎么", "如何", "比较", "相比", "一样", "相同", "不同");
    private static final Set<String> NON_ENTITY_OPERANDS = Set.of(
            "花友", "网友", "大家", "社区", "用户", "指南", "经验", "建议", "方法", "要求",
            "光照", "温度", "湿度", "浇水", "补水", "施肥", "土壤", "状态", "环境", "情况");

    List<String> find(String normalizedQuery, List<PlantMention> knownMentions, PlantCatalogSnapshot catalog) {
        if (normalizedQuery == null || normalizedQuery.isBlank() || knownMentions.isEmpty()) return List.of();
        Set<String> unresolved = new LinkedHashSet<>();
        for (PlantMention mention : knownMentions) {
            if (mention.end() < normalizedQuery.length() && separator(normalizedQuery.charAt(mention.end()))) {
                addIfUnresolved(rightOperand(normalizedQuery, mention.end() + 1), catalog, unresolved);
            }
            if (mention.start() > 0 && separator(normalizedQuery.charAt(mention.start() - 1))) {
                addIfUnresolved(leftOperand(normalizedQuery, mention.start() - 1), catalog, unresolved);
            }
        }
        return List.copyOf(unresolved);
    }

    private String rightOperand(String query, int start) {
        int end = query.length();
        for (int i = start; i < query.length(); i++) {
            if (separator(query.charAt(i)) || punctuation(query.charAt(i))) {
                end = i;
                break;
            }
        }
        String value = query.substring(start, end);
        for (String boundary : RIGHT_BOUNDARIES) {
            int index = value.indexOf(boundary);
            if (index >= 0) value = value.substring(0, index);
        }
        return value;
    }

    private String leftOperand(String query, int separatorIndex) {
        int start = separatorIndex - 1;
        while (start >= 0 && !separator(query.charAt(start)) && !punctuation(query.charAt(start))) start--;
        String value = query.substring(start + 1, separatorIndex);
        int possessive = value.lastIndexOf('的');
        return possessive >= 0 ? value.substring(possessive + 1) : value;
    }

    private void addIfUnresolved(String value, PlantCatalogSnapshot catalog, Set<String> result) {
        String candidate = value == null ? "" : value.trim();
        if (!candidate.matches("[\\p{IsHan}]{2,12}")
                || NON_ENTITY_OPERANDS.stream().anyMatch(candidate::startsWith)) return;
        if (!catalog.mentionMatcher().find(candidate).isEmpty()) return;
        result.add(candidate);
    }

    private boolean separator(char value) { return SEPARATORS.indexOf(value) >= 0; }
    private boolean punctuation(char value) { return "。！？?!：:".indexOf(value) >= 0; }
}
