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
    private static final Pattern CONTEXTUAL_RIGHT_HINT = Pattern.compile(
            "^(?:的|浇水|补水|施肥|修剪|光照|阳光|温度|湿度|肥料|土壤|养护|黄叶|发黄|枯黄|"
                    + "叶片|叶基|叶子|根腐|缺水|晒|太阳|浇|补|出现|处理|频率|耐阴|喜阴|弱光|"
                    + "强光|直射|状态|异常|判断|表现|检查)");
    private static final Pattern HAN_NAME = Pattern.compile("[\\p{IsHan}]+");
    MatchResult match(String query, String mentionQuery, String namedSubject, List<PlantCatalogEntry> entries) {
        MatchResult entityChain = entityChainMatch(query, entries);
        if (entityChain.status() != MatchStatus.NONE) return entityChain;

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
        boolean alias = matches.stream().anyMatch(entry -> entry.aliases().contains(matchedName));
        if (matches.size() > 1) {
            return MatchResult.candidates(matches, alias, matchedName, ambiguousReason);
        }
        PlantCatalogEntry entry = matches.get(0);
        return MatchResult.known(matches, entry.aliases().contains(matchedName), matchedName);
    }

    private MatchResult uniqueNameMatch(List<NameMatch> matches, String ambiguousReason) {
        if (matches.isEmpty()) return MatchResult.none();
        Set<String> matchedNames = matches.stream().map(NameMatch::matchedName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (matches.size() > 1 && matchedNames.size() == 1) {
            String matchedName = matchedNames.iterator().next();
            List<PlantCatalogEntry> entries = matches.stream().map(NameMatch::entry).distinct().toList();
            boolean alias = entries.stream().anyMatch(entry -> entry.aliases().contains(matchedName));
            return MatchResult.candidates(entries, alias, matchedName, ambiguousReason);
        }
        if (matches.size() > 1) return MatchResult.ambiguous(ambiguousReason, matches.size());
        NameMatch match = matches.get(0);
        return MatchResult.known(List.of(match.entry()), match.entry().aliases().contains(match.matchedName()),
                match.matchedName());
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

    private MatchResult entityChainMatch(String query, List<PlantCatalogEntry> entries) {
        List<NameMatch> mentions = entityChainMentions(query, entries);
        if (mentions.size() < 2) return MatchResult.none();

        List<NameMatch> chain = new ArrayList<>();
        for (NameMatch mention : mentions) {
            if (chain.isEmpty()) {
                chain.add(mention);
            } else if (isWeakEntityLink(query.substring(chain.get(chain.size() - 1).end(), mention.start()))) {
                chain.add(mention);
            } else if (chain.size() < 2) {
                chain.clear();
                chain.add(mention);
            } else {
                break;
            }
        }
        if (chain.size() < 2) return MatchResult.none();

        List<PlantCatalogEntry> matchedEntries = chain.stream().map(NameMatch::entry).distinct().toList();
        boolean alias = chain.stream().anyMatch(match -> match.entry().aliases().contains(match.matchedName()));
        return MatchResult.known(matchedEntries, alias, "");
    }

    private List<NameMatch> entityChainMentions(String query, List<PlantCatalogEntry> entries) {
        List<NameMatch> matches = new ArrayList<>();
        for (PlantCatalogEntry entry : entries) {
            for (String name : entry.names()) {
                int from = 0;
                while (from <= query.length() - name.length()) {
                    int start = query.indexOf(name, from);
                    if (start < 0) break;
                    int end = start + name.length();
                    if (hasStandaloneBoundary(query, start, end, name)
                            || hasContextualBoundary(query, start, end)
                            || isComparisonSuffix(query.substring(end))) {
                        matches.add(new NameMatch(entry, name, start));
                    }
                    from = start + 1;
                }
            }
        }
        return matches.stream()
                .sorted(Comparator.comparingInt(NameMatch::start)
                        .thenComparing(match -> match.matchedName().length(), Comparator.reverseOrder()))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(NameMatch::start, match -> match,
                                (left, right) -> left, LinkedHashMap::new),
                        matchesByStart -> List.copyOf(matchesByStart.values())));
    }

    private boolean isWeakEntityLink(String value) {
        if (value.isBlank()) return false;
        return value.codePoints().allMatch(codePoint -> Character.isWhitespace(codePoint)
                || codePoint == '和' || codePoint == '与' || codePoint == '跟'
                || isPunctuation(codePoint));
    }

    private boolean isPunctuation(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private boolean isComparisonSuffix(String suffix) {
        return suffix.isBlank() || suffix.startsWith("的") || suffix.startsWith("适宜")
                || suffix.startsWith("需要") || suffix.startsWith("应该") || suffix.startsWith("是否")
                || CARE_ANCHOR.matcher(suffix).find();
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

    enum MatchStatus { NONE, KNOWN, CANDIDATES, AMBIGUOUS, UNKNOWN }

    record MatchResult(MatchStatus status, List<PlantCatalogEntry> entries, boolean alias,
                       String mention, String reason, int candidateCount) {
        static MatchResult none() {
            return new MatchResult(MatchStatus.NONE, List.of(), false, "", "", 0);
        }
        static MatchResult known(List<PlantCatalogEntry> entries, boolean alias, String mention) {
            return new MatchResult(MatchStatus.KNOWN, List.copyOf(entries), alias, mention, "", entries.size());
        }
        static MatchResult candidates(List<PlantCatalogEntry> entries, boolean alias,
                                      String mention, String reason) {
            return new MatchResult(MatchStatus.CANDIDATES, List.copyOf(entries), alias,
                    mention, reason, entries.size());
        }
        static MatchResult ambiguous(String reason, int count) {
            return new MatchResult(MatchStatus.AMBIGUOUS, List.of(), false, "", reason, count);
        }
        static MatchResult unknown(String reason) {
            return new MatchResult(MatchStatus.UNKNOWN, List.of(), false, "", reason, 0);
        }
    }

    private record NameMatch(PlantCatalogEntry entry, String matchedName, int start) {
        private int end() {
            return start + matchedName.length();
        }

        private boolean preferredTo(NameMatch other) {
            if (matchedName.length() != other.matchedName.length()) return matchedName.length() > other.matchedName.length();
            return start < other.start;
        }
    }
}
