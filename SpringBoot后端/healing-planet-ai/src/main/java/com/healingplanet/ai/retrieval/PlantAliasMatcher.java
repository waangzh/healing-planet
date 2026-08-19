package com.healingplanet.ai.retrieval;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PlantAliasMatcher {
    private static final Pattern CARE_ANCHOR = Pattern.compile(
            "浇水|补水|施肥|修剪|光照|阳光|温度|湿度|肥料|土壤|养护|黄叶|发黄|枯黄|叶片|"
                    + "晒|太阳|浇|补|出现|处理|频率|耐阴|喜阴|弱光|强光|直射|状态|异常|判断");
    private static final Pattern COMPARISON_SEPARATOR = Pattern.compile("[和与跟]");
    private static final Pattern COMPARISON_TERM = Pattern.compile("和|与|跟|比较|对比|相比|是否相同|一样|相同");
    private static final Pattern CONTEXTUAL_RIGHT_HINT = Pattern.compile(
            "^(?:的|浇水|补水|施肥|修剪|光照|阳光|温度|湿度|肥料|土壤|养护|黄叶|发黄|枯黄|"
                    + "叶片|叶基|叶子|根腐|缺水|晒|太阳|浇|补|出现|处理|频率|耐阴|喜阴|弱光|"
                    + "强光|直射|状态|异常|判断|表现|检查)");
    private static final Pattern HAN_NAME = Pattern.compile("[\\p{IsHan}]+");
    private static final Set<String> NON_ENTITY_TERMS = Set.of(
            "植物", "绿植", "盆栽", "花卉", "光照", "阳光", "浇水", "补水", "温度", "湿度", "施肥",
            "肥料", "土壤", "修剪", "养护", "黄叶", "发黄", "枯黄", "叶片", "叶子", "耐阴", "喜阴",
            "弱光", "强光", "直射", "状态", "异常", "判断", "处理"
    );

    MatchResult match(String query, String mentionQuery, String namedSubject, List<PlantCatalogEntry> entries) {
        MatchResult comparison = comparisonMatch(query, entries);
        if (comparison.status() != MatchStatus.NONE) return comparison;

        MatchResult subject = uniqueMatch(exactMatches(namedSubject, entries), namedSubject,
                "multiple_exact_entity_matches");
        if (subject.status() != MatchStatus.NONE) return subject;

        List<NameMatch> standalone = standaloneMatches(query, entries);
        MatchResult exact = uniqueNameMatch(standalone, "multiple_exact_entity_matches");
        if (exact.status() != MatchStatus.NONE) return exact;

        List<NameMatch> contextual = contextualMatches(query, entries);
        MatchResult contextualResult = uniqueNameMatch(contextual, "multiple_contextual_entity_matches");
        if (contextualResult.status() != MatchStatus.NONE) return contextualResult;

        List<NameMatch> leading = leadingMatches(mentionQuery, entries);
        return uniqueNameMatch(leading, "multiple_leading_entity_matches");
    }

    boolean containsCatalogName(String query, List<PlantCatalogEntry> entries) {
        return entries.stream().anyMatch(entry -> entry.names().stream().anyMatch(query::contains));
    }

    Set<String> contextualEntryIds(String query, List<PlantCatalogEntry> entries) {
        Set<String> result = new LinkedHashSet<>();
        contextualMatches(query, entries).forEach(match -> result.add(match.entry().canonicalPlantId()));
        return Set.copyOf(result);
    }

    String catalogMention(String query, List<PlantCandidateGenerator.Candidate> candidates) {
        return candidates.stream()
                .filter(PlantCandidateGenerator.Candidate::hasExactCatalogName)
                .flatMap(candidate -> candidate.entry().names().stream())
                .filter(name -> name.length() >= 2 && query.contains(name))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .findFirst()
                .orElse("");
    }

    private MatchResult uniqueMatch(List<PlantCatalogEntry> matches, String matchedName, String ambiguousReason) {
        if (matches.isEmpty()) return MatchResult.none();
        if (matches.size() > 1) return MatchResult.ambiguous(ambiguousReason, matches.size());
        PlantCatalogEntry entry = matches.get(0);
        return MatchResult.known(matches, entry.aliases().contains(matchedName));
    }

    private MatchResult uniqueNameMatch(List<NameMatch> matches, String ambiguousReason) {
        if (matches.isEmpty()) return MatchResult.none();
        if (matches.size() > 1) return MatchResult.ambiguous(ambiguousReason, matches.size());
        NameMatch match = matches.get(0);
        return MatchResult.known(List.of(match.entry()), match.entry().aliases().contains(match.matchedName()));
    }

    private List<PlantCatalogEntry> exactMatches(String mention, List<PlantCatalogEntry> entries) {
        if (mention == null || mention.isBlank()) return List.of();
        return entries.stream()
                .filter(entry -> entry.names().contains(mention))
                .toList();
    }

    private List<NameMatch> standaloneMatches(String query, List<PlantCatalogEntry> entries) {
        List<NameMatch> matches = new ArrayList<>();
        for (PlantCatalogEntry entry : entries) {
            entry.names().stream().filter(name -> hasStandaloneName(query, name)).findFirst()
                    .ifPresent(name -> matches.add(new NameMatch(entry, name, query.indexOf(name))));
        }
        return matches;
    }

    private List<NameMatch> contextualMatches(String query, List<PlantCatalogEntry> entries) {
        Map<String, NameMatch> matches = new LinkedHashMap<>();
        for (PlantCatalogEntry entry : entries) {
            for (String name : entry.names()) {
                int from = 0;
                while (from <= query.length() - name.length()) {
                    int start = query.indexOf(name, from);
                    if (start < 0) break;
                    int end = start + name.length();
                    if (hasStandaloneBoundary(query, start, end, name) || hasContextualBoundary(query, start, end)) {
                        NameMatch candidate = new NameMatch(entry, name, start);
                        NameMatch current = matches.get(entry.canonicalPlantId());
                        if (current == null || candidate.preferredTo(current)) {
                            matches.put(entry.canonicalPlantId(), candidate);
                        }
                    }
                    from = start + 1;
                }
            }
        }
        return matches.values().stream().sorted(Comparator.comparingInt(NameMatch::start)).toList();
    }

    private List<NameMatch> leadingMatches(String query, List<PlantCatalogEntry> entries) {
        int longestName = entries.stream().flatMap(entry -> entry.names().stream())
                .filter(query::startsWith).mapToInt(String::length).max().orElse(0);
        if (longestName == 0) return List.of();
        List<NameMatch> matches = new ArrayList<>();
        for (PlantCatalogEntry entry : entries) {
            entry.names().stream()
                    .filter(name -> name.length() == longestName && query.startsWith(name))
                    .findFirst().ifPresent(name -> matches.add(new NameMatch(entry, name, 0)));
        }
        return matches;
    }

    private MatchResult comparisonMatch(String query, List<PlantCatalogEntry> entries) {
        if (!COMPARISON_TERM.matcher(query).find()) return MatchResult.none();
        Matcher separator = COMPARISON_SEPARATOR.matcher(query);
        if (!separator.find()) return MatchResult.none();

        String left = comparisonSubject(query.substring(0, separator.start()), false);
        String right = comparisonSubject(query.substring(separator.end()), true);
        NameMatch leftMatch = comparisonEntry(left, entries);
        NameMatch rightMatch = comparisonEntry(right, entries);
        boolean leftKnown = leftMatch != null;
        boolean rightKnown = rightMatch != null;
        boolean detected = (leftKnown || rightKnown)
                && (leftKnown || looksLikeUnknownMention(left, false))
                && (rightKnown || looksLikeUnknownMention(right, true));
        if (!detected) return MatchResult.none();
        if (!leftKnown || !rightKnown) return MatchResult.unknown("comparison_entity_unresolved");
        boolean alias = leftMatch.entry().aliases().contains(leftMatch.matchedName())
                || rightMatch.entry().aliases().contains(rightMatch.matchedName());
        return MatchResult.known(List.of(leftMatch.entry(), rightMatch.entry()), alias);
    }

    private String comparisonSubject(String value, boolean rightSide) {
        String subject = value.replaceFirst("^(请问|想问下|我想问|比较|对比)", "");
        if (rightSide) {
            int possessive = subject.indexOf('的');
            if (possessive > 0) subject = subject.substring(0, possessive);
        }
        return subject.replaceAll("[？?。，,;；]", "");
    }

    private NameMatch comparisonEntry(String mention, List<PlantCatalogEntry> entries) {
        for (PlantCatalogEntry entry : entries) {
            for (String name : entry.names()) {
                if (name.equals(mention) || mention.startsWith(name) && isComparisonSuffix(mention.substring(name.length()))) {
                    return new NameMatch(entry, name, 0);
                }
            }
        }
        return null;
    }

    private boolean isComparisonSuffix(String suffix) {
        return suffix.isBlank() || suffix.startsWith("的") || suffix.startsWith("适宜")
                || suffix.startsWith("需要") || suffix.startsWith("应该") || suffix.startsWith("是否")
                || CARE_ANCHOR.matcher(suffix).find();
    }

    private boolean looksLikeUnknownMention(String text, boolean rightSide) {
        String candidate = comparisonSubject(text, rightSide);
        if (candidate.isBlank() || NON_ENTITY_TERMS.contains(candidate)) return false;
        return candidate.length() >= 2 && candidate.length() <= 30 && HAN_NAME.matcher(candidate).matches();
    }

    private boolean hasStandaloneName(String query, String name) {
        int from = 0;
        while (from <= query.length() - name.length()) {
            int start = query.indexOf(name, from);
            if (start < 0) return false;
            int end = start + name.length();
            if (hasStandaloneBoundary(query, start, end, name)) return true;
            from = start + 1;
        }
        return false;
    }

    private boolean hasStandaloneBoundary(String query, int start, int end, String name) {
        if (!HAN_NAME.matcher(name).matches()) {
            boolean left = start == 0 || !isAsciiLetterOrDigit(query.charAt(start - 1));
            boolean right = end == query.length() || !isAsciiLetterOrDigit(query.charAt(end));
            return left && right;
        }
        boolean left = start == 0 || !isHan(query.charAt(start - 1));
        boolean right = end == query.length() || !isHan(query.charAt(end));
        return left && right;
    }

    private boolean hasContextualBoundary(String query, int start, int end) {
        boolean contextualLeft = start > 0 && endsWithAny(query.substring(0, start),
                "的", "里", "中", "上", "下", "盆", "株", "到", "养", "有", "没", "问", "看");
        boolean left = start == 0 || !isHan(query.charAt(start - 1)) || contextualLeft;
        boolean right = end >= query.length() || !isHan(query.charAt(end))
                || CONTEXTUAL_RIGHT_HINT.matcher(query.substring(end)).find();
        return left && (right || contextualLeft);
    }

    private boolean endsWithAny(String value, String... suffixes) {
        for (String suffix : suffixes) if (value.endsWith(suffix)) return true;
        return false;
    }

    private boolean isHan(char value) {
        return Character.UnicodeScript.of(value) == Character.UnicodeScript.HAN;
    }

    private boolean isAsciiLetterOrDigit(char value) {
        return value >= 'a' && value <= 'z' || value >= '0' && value <= '9';
    }

    enum MatchStatus { NONE, KNOWN, AMBIGUOUS, UNKNOWN }

    record MatchResult(MatchStatus status, List<PlantCatalogEntry> entries, boolean alias,
                       String reason, int candidateCount) {
        static MatchResult none() { return new MatchResult(MatchStatus.NONE, List.of(), false, "", 0); }
        static MatchResult known(List<PlantCatalogEntry> entries, boolean alias) {
            return new MatchResult(MatchStatus.KNOWN, List.copyOf(entries), alias, "", entries.size());
        }
        static MatchResult ambiguous(String reason, int count) {
            return new MatchResult(MatchStatus.AMBIGUOUS, List.of(), false, reason, count);
        }
        static MatchResult unknown(String reason) {
            return new MatchResult(MatchStatus.UNKNOWN, List.of(), false, reason, 0);
        }
    }

    private record NameMatch(PlantCatalogEntry entry, String matchedName, int start) {
        private boolean preferredTo(NameMatch other) {
            if (matchedName.length() != other.matchedName.length()) return matchedName.length() > other.matchedName.length();
            return start < other.start;
        }
    }
}
