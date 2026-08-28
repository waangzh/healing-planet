package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.query.QueryLexicon;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects unresolved plant mentions without participating in known-entity linking.
 * Coordinated operands and standalone generic-concept queries remain separate deterministic cases.
 */
final class UnresolvedPlantMentionDetector {
    private static final String SEPARATORS = "和与跟及、/,，；;";
    private static final List<String> RIGHT_BOUNDARIES = List.of(
            "的", "哪个", "哪种", "谁", "是否", "是不是", "怎么", "如何", "比较", "相比", "一样", "相同", "不同");
    private static final Set<String> NON_ENTITY_OPERANDS = Set.of(
            "花友", "网友", "大家", "社区", "用户", "指南", "经验", "建议", "方法", "要求",
            "光照", "温度", "湿度", "浇水", "补水", "施肥", "土壤", "状态", "环境", "情况");
    private static final Set<String> PLANT_NAME_SUFFIXES = Set.of(
            "苔藓", "兰", "榕", "蕨", "竹", "藤", "菊", "莲", "掌", "草", "花", "树", "木");
    private static final Set<String> GENERIC_PLANT_CATEGORIES = Set.of(
            "苔藓", "兰花", "小草", "仙人掌", "花草", "树木", "花", "草", "树", "木");

    StandaloneMentionKind classifyGenericConcept(String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()
                || !QueryLexicon.containsAny(normalizedQuery, QueryLexicon.GENERIC_CONCEPT)
                || QueryLexicon.containsAny(normalizedQuery, QueryLexicon.CLEAR_NON_PLANT)) {
            return StandaloneMentionKind.NO_ENTITY;
        }
        for (String suffix : PLANT_NAME_SUFFIXES) {
            int suffixIndex = normalizedQuery.indexOf(suffix);
            if (suffixIndex <= 0) continue;
            int suffixEnd = suffixIndex + suffix.length();
            String candidate = normalizedQuery.substring(0, suffixEnd);
            if (GENERIC_PLANT_CATEGORIES.contains(candidate)) continue;
            String following = normalizedQuery.substring(suffixEnd);
            if (following.startsWith("是什么意思") || following.startsWith("怎么理解")
                    || following.startsWith("的光合作用") || following.startsWith("的蒸腾作用")
                    || following.startsWith("的呼吸作用")) {
                return StandaloneMentionKind.UNRESOLVED_ENTITY_MENTION;
            }
        }
        return StandaloneMentionKind.NO_ENTITY;
    }

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

    enum StandaloneMentionKind {
        NO_ENTITY,
        UNRESOLVED_ENTITY_MENTION
    }
}
