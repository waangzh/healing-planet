package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.ingestion.KnowledgeRepository;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PlantEntityResolver {

    private static final Pattern CARE_ANCHOR = Pattern.compile(
            "浇水|补水|施肥|修剪|光照|阳光|温度|湿度|肥料|土壤|养护|黄叶|发黄|枯黄|叶片|晒|太阳|浇|补");
    private static final Pattern GENERIC_PLANT_QUERY = Pattern.compile(
            "^(?:请问|想问下|我想问|请|帮我)?(?:"
                    + "(?:什么|哪种|哪些|哪类|有哪些).*?(?:植物|绿植|盆栽|花卉)|"
                    + "(?:这种)?(?:植物|绿植|盆栽|花卉)(?:有哪些|推荐|比较好|的|叶|怎么|如何|适合|需要)|"
                    + "(?:适合|推荐).*(?:宿舍|室内|办公室|卧室|家里).*(?:植物|绿植|盆栽|花卉)|"
                    + "(?:宿舍|室内|办公室|卧室|家里).*(?:植物|绿植|盆栽|花卉)(?:有哪些|推荐|比较好))");
    private static final Pattern LEADING_MENTION_NOISE = Pattern.compile(
            "^(?:请问|想问下|我想问|帮我看看|帮我看下|我的|我这盆|这盆|家里的|一盆|一株|这株)+");
    private static final Pattern LEADING_TIME_CONTEXT = Pattern.compile(
            "^(?:(?:每|一)(?:天|周|星期|个星期|月)|平时|平常)(?:给)?");
    private static final Pattern TRAILING_MENTION_NOISE = Pattern.compile(
            "(?:(?:每|一)(?:天|周|星期|个星期|月)|平时|平常|应该|应当|需要|适合|是否|"
                    + "是不是|怎么|如何|多久|多长时间|在什么情况下|什么情况下|要不要|"
                    + "可以不可以|耐不耐|能不能|不能|能|一直|老|该|什么|的)+$");
    private static final Pattern COMPARISON_SEPARATOR = Pattern.compile("[和与跟]");
    private static final Pattern COMPARISON_TERM = Pattern.compile("和|与|跟|比较|对比|相比|是否相同|一样|相同");
    private static final Pattern HAN_NAME = Pattern.compile("[\\p{IsHan}]+");
    private static final Set<String> CARE_TERMS = Set.of(
            "光照", "阳光", "浇水", "补水", "温度", "湿度", "施肥", "肥料", "土壤", "修剪", "养护", "黄叶", "发黄", "枯黄", "叶片", "叶子"
    );
    private static final Set<String> PLANT_DOMAIN_TERMS = Set.of(
            "植物", "绿植", "盆栽", "花卉", "花盆", "花草", "植株", "园艺", "种植", "栽培", "多肉",
            "养花", "养植物", "养绿植", "盆土", "根系", "叶片"
    );
    private static final Set<String> GENERIC_SUBJECTS = Set.of(
            "植物", "什么植物", "哪种植物", "哪些植物", "这种植物", "新手", "室内", "家里", "办公室", "卧室", "宿舍"
    );
    private static final Set<String> EXACT_NAME_PREFIXES = Set.of(
            "的", "这盆", "我的", "家里的", "一盆", "一株", "这株", "养", "种", "关于", "比较", "和", "与", "跟"
    );
    private static final Set<String> EXACT_NAME_FOLLOWERS = Set.of(
            "的", "适合", "需要", "应该", "应当", "是否", "是不是", "怎么", "如何", "多久", "要不要",
            "可以", "耐不耐", "能不能", "能", "对", "一周", "每天", "几天", "几回", "湿度", "温度", "光照", "浇水",
            "和", "与", "跟", "比较", "对比", "相比", "一样", "相同"
    );

    private final KnowledgeRepository repository;
    private final VectorStore entityStore;
    private final SparseIndexService sparseIndex;
    private final RagProperties.EntityResolution properties;
    private final RetrievalMetrics metrics;
    private volatile List<PlantEntry> catalog;

    public PlantEntityResolver(KnowledgeRepository repository) {
        this(repository, null, null, new RagProperties(), null);
    }

    @Autowired
    public PlantEntityResolver(KnowledgeRepository repository,
                               @Qualifier("plantEntityVectorStore") VectorStore entityStore,
                               SparseIndexService sparseIndex, RagProperties ragProperties,
                               RetrievalMetrics metrics) {
        this.repository = repository;
        this.entityStore = entityStore;
        this.sparseIndex = sparseIndex;
        this.properties = ragProperties.getEntityResolution();
        this.metrics = metrics;
    }

    public Resolution resolve(RagQuery query) {
        List<PlantEntry> entries = catalog();
        if (query.canonicalPlantId() != null && !query.canonicalPlantId().isBlank()) {
            return entries.stream()
                    .filter(entry -> query.canonicalPlantId().equals(entry.canonicalPlantId()))
                    .findFirst().map(entry -> Resolution.known(entry, ResolutionMethod.EXPLICIT_ID,
                            1, 0, 1))
                    .orElseGet(() -> Resolution.unknown("canonical_plant_id_not_found", 0, 0, 0));
        }

        String normalizedQuery = normalize(query.query());
        List<PlantEntry> exactMatches = entries.stream()
                .filter(entry -> entry.names().stream().anyMatch(name -> hasBoundedName(normalizedQuery, name)))
                .toList();
        ComparisonMentions comparison = comparisonMentions(normalizedQuery, exactMatches);
        if (comparison.detected() && !comparison.complete()) {
            return Resolution.unknown("comparison_entity_unresolved", 1, 0, exactMatches.size());
        }
        if (!exactMatches.isEmpty()) {
            return Resolution.known(exactMatches, ResolutionMethod.EXACT_NAME, 1, 0, exactMatches.size());
        }
        String namedSubject = extractPotentialMention(query.query());
        if (!namedSubject.isBlank() && entries.stream().flatMap(entry -> entry.names().stream())
                .anyMatch(namedSubject::contains)) {
            return Resolution.unknown("known_name_embedded_in_unknown_compound", 1, 0, 1);
        }
        if (isExplicitGenericPlantQuery(normalizedQuery)) return Resolution.generic();

        boolean plantDomain = isPlantDomainQuery(normalizedQuery);
        Resolution editDistanceResolution = resolveUniqueShortTypo(query.query(), entries, plantDomain);
        if (editDistanceResolution != null) return editDistanceResolution;

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        addCharacterCandidates(normalizedQuery, entries, candidates);
        if (plantDomain || !candidates.isEmpty()) {
            addSparseCandidates(query.query(), entries, candidates);
            addVectorCandidates(query.query(), entries, candidates);
        }

        List<Candidate> ranked = candidates.values().stream()
                .sorted((left, right) -> {
                    if (left.vectorScore() > 0 || right.vectorScore() > 0) {
                        int vectorOrder = Double.compare(right.vectorScore(), left.vectorScore());
                        if (vectorOrder != 0) return vectorOrder;
                    }
                    return Double.compare(right.rankScore(), left.rankScore());
                })
                .limit(properties.getCandidateTopK())
                .toList();
        recordCandidateCount(ranked.size());
        Resolution candidateResolution = resolveCandidates(ranked);
        if (candidateResolution != null) return candidateResolution;

        if (!plantDomain) return Resolution.outOfDomain();
        String rejectionReason = namedSubject.isBlank()
                ? "plant_query_without_confirmed_entity"
                : "no_acceptable_entity_candidate";
        return Resolution.unknown(rejectionReason, topScore(ranked), secondScore(ranked), ranked.size());
    }

    public boolean matches(Resolution resolution, KnowledgeDocument document) {
        if (resolution.kind() == ResolutionKind.GENERIC) return true;
        if (resolution.kind() == ResolutionKind.UNKNOWN || resolution.kind() == ResolutionKind.AMBIGUOUS
                || resolution.kind() == ResolutionKind.OUT_OF_DOMAIN) return false;
        if (resolution.canonicalPlantIds().contains(document.canonicalPlantId())) return true;
        if (document.canonicalPlantId() != null && !document.canonicalPlantId().isBlank()) return false;

        String searchable = normalize(document.plantName() + " " + document.title() + " " + document.content());
        return resolution.names().stream().anyMatch(searchable::contains);
    }

    private boolean isPlantDomainQuery(String normalizedQuery) {
        return CARE_TERMS.stream().anyMatch(normalizedQuery::contains)
                || PLANT_DOMAIN_TERMS.stream().anyMatch(normalizedQuery::contains)
                || normalizedQuery.contains("浇") || normalizedQuery.contains("补")
                || normalizedQuery.contains("晒") || normalizedQuery.contains("太阳");
    }

    private boolean isExplicitGenericPlantQuery(String normalizedQuery) {
        return GENERIC_PLANT_QUERY.matcher(normalizedQuery).find();
    }

    private Resolution resolveUniqueShortTypo(String rawQuery, List<PlantEntry> entries, boolean plantDomain) {
        if (!plantDomain) return null;
        String subject = extractPotentialMention(rawQuery);
        if (subject.isBlank()) return null;

        List<TypoCandidate> candidates = new ArrayList<>();
        for (PlantEntry entry : entries) {
            for (String name : entry.names()) {
                if (!isShortChineseName(name) || Math.abs(subject.length() - name.length()) > 1) continue;
                int distance = levenshtein(subject, name);
                if (distance == 1 && commonPrefixLength(subject, name) >= 2) {
                    candidates.add(new TypoCandidate(entry,
                            1d - (double) distance / Math.max(subject.length(), name.length())));
                    break;
                }
            }
        }
        if (candidates.size() != 1) return null;
        TypoCandidate candidate = candidates.get(0);
        return Resolution.known(candidate.entry(), ResolutionMethod.EDIT_DISTANCE,
                candidate.score(), 0, 1);
    }

    private String extractPotentialMention(String query) {
        String normalized = normalize(query).replaceAll("[？?。，,;；！!]", "");
        Matcher anchor = CARE_ANCHOR.matcher(normalized);
        if (!anchor.find()) return "";

        String candidate = normalized.substring(0, anchor.start());
        candidate = LEADING_MENTION_NOISE.matcher(candidate).replaceFirst("");
        candidate = LEADING_TIME_CONTEXT.matcher(candidate).replaceFirst("");
        candidate = candidate.replaceFirst("^给", "");
        candidate = TRAILING_MENTION_NOISE.matcher(candidate).replaceFirst("");
        if (candidate.isBlank() || GENERIC_SUBJECTS.contains(candidate) || candidate.length() > 30) return "";
        return candidate;
    }

    private boolean isShortChineseName(String name) {
        return name.length() >= 3 && name.length() <= 4 && HAN_NAME.matcher(name).matches();
    }

    private int commonPrefixLength(String left, String right) {
        int length = Math.min(left.length(), right.length());
        int index = 0;
        while (index < length && left.charAt(index) == right.charAt(index)) index++;
        return index;
    }

    private boolean hasBoundedName(String query, String name) {
        int from = 0;
        while (from <= query.length() - name.length()) {
            int start = query.indexOf(name, from);
            if (start < 0) return false;
            int end = start + name.length();
            if (hasNameBoundary(query, start, end, name)) return true;
            from = start + 1;
        }
        return false;
    }

    private boolean hasNameBoundary(String query, int start, int end, String name) {
        if (!HAN_NAME.matcher(name).matches()) {
            boolean left = start == 0 || !isAsciiLetterOrDigit(query.charAt(start - 1));
            boolean right = end == query.length() || !isAsciiLetterOrDigit(query.charAt(end));
            return left && right;
        }
        String left = query.substring(0, start);
        String right = query.substring(end);
        boolean leftBoundary = left.isEmpty() || !isHan(left.charAt(left.length() - 1))
                || EXACT_NAME_PREFIXES.stream().anyMatch(left::endsWith);
        boolean rightBoundary = right.isEmpty() || !isHan(right.charAt(0))
                || EXACT_NAME_FOLLOWERS.stream().anyMatch(right::startsWith)
                || CARE_TERMS.stream().anyMatch(right::startsWith)
                || PLANT_DOMAIN_TERMS.stream().anyMatch(right::startsWith);
        return leftBoundary && rightBoundary;
    }

    private boolean isHan(char value) {
        return Character.UnicodeScript.of(value) == Character.UnicodeScript.HAN;
    }

    private boolean isAsciiLetterOrDigit(char value) {
        return value >= 'a' && value <= 'z' || value >= '0' && value <= '9';
    }

    private ComparisonMentions comparisonMentions(String query, List<PlantEntry> exactMatches) {
        if (!COMPARISON_TERM.matcher(query).find()) return ComparisonMentions.none();
        Matcher separator = COMPARISON_SEPARATOR.matcher(query);
        if (!separator.find()) {
            return exactMatches.size() >= 2 ? ComparisonMentions.completeComparison() : ComparisonMentions.none();
        }

        String left = query.substring(0, separator.start());
        String right = query.substring(separator.end());
        boolean leftKnown = containsEntryName(left, exactMatches);
        boolean rightKnown = containsEntryName(right, exactMatches);
        boolean leftMention = leftKnown || looksLikeUnknownMention(left, false);
        boolean rightMention = rightKnown || looksLikeUnknownMention(right, true);
        boolean detected = (leftKnown || rightKnown) && leftMention && rightMention;
        return detected ? new ComparisonMentions(true, leftKnown && rightKnown) : ComparisonMentions.none();
    }

    private boolean containsEntryName(String text, List<PlantEntry> entries) {
        return entries.stream().anyMatch(entry -> entry.names().stream().anyMatch(text::contains));
    }

    private boolean looksLikeUnknownMention(String text, boolean rightSide) {
        String candidate = text.replaceFirst("^(请问|想问下|我想问|比较|对比)", "");
        if (rightSide) {
            int possessive = candidate.indexOf('的');
            if (possessive > 0) candidate = candidate.substring(0, possessive);
        }
        candidate = candidate.replaceAll("[？?。，,;；]", "");
        if (candidate.isBlank() || CARE_TERMS.contains(candidate) || PLANT_DOMAIN_TERMS.contains(candidate)) return false;
        return candidate.length() >= 2 && candidate.length() <= 30 && HAN_NAME.matcher(candidate).matches();
    }

    private double topScore(List<Candidate> ranked) {
        return ranked.isEmpty() ? 0 : ranked.get(0).vectorScore() > 0
                ? ranked.get(0).vectorScore() : ranked.get(0).characterScore();
    }

    private double secondScore(List<Candidate> ranked) {
        if (ranked.size() < 2) return 0;
        Candidate second = ranked.get(1);
        return ranked.get(0).vectorScore() > 0 ? second.vectorScore() : second.characterScore();
    }

    private Resolution resolveCandidates(List<Candidate> ranked) {
        if (ranked.isEmpty()) return null;
        Candidate top = ranked.get(0);
        Candidate second = ranked.size() > 1 ? ranked.get(1) : Candidate.empty();

        double topScore;
        double secondScore;
        boolean accepted;
        ResolutionMethod method;
        if (top.vectorScore() > 0) {
            topScore = top.vectorScore();
            secondScore = second.vectorScore();
            double margin = topScore - secondScore;
            boolean corroborated = top.characterScore() >= properties.getCharacterCorroborationThreshold()
                    || top.sparseScore() > 0
                    || topScore >= properties.getStrongVectorThreshold();
            accepted = topScore >= properties.getVectorAcceptanceThreshold()
                    && margin >= properties.getMarginThreshold() && corroborated;
            method = top.characterScore() > 0 || top.sparseScore() > 0
                    ? ResolutionMethod.HYBRID : ResolutionMethod.VECTOR;
        } else {
            topScore = top.characterScore();
            secondScore = second.characterScore();
            accepted = topScore >= properties.getLexicalAcceptanceThreshold()
                    && topScore - secondScore >= properties.getMarginThreshold();
            method = ResolutionMethod.LEXICAL;
        }

        if (accepted) {
            return Resolution.known(top.entry(), method, topScore, secondScore, ranked.size());
        }
        boolean ambiguous = top.vectorScore() > 0
                ? topScore >= properties.getVectorAcceptanceThreshold()
                : top.characterScore() >= properties.getLexicalAcceptanceThreshold();
        if (ambiguous) {
            return Resolution.ambiguous(topScore, secondScore, ranked.size(), "candidate_margin_or_corroboration_too_low");
        }
        return null;
    }

    private void addCharacterCandidates(String query, List<PlantEntry> entries,
                                        Map<String, Candidate> candidates) {
        for (PlantEntry entry : entries) {
            double score = entry.names().stream()
                    .mapToDouble(name -> bestSubstringSimilarity(query, name)).max().orElse(0);
            if (score >= properties.getCharacterCorroborationThreshold()) {
                candidate(candidates, entry).characterScore(score);
            }
        }
    }

    private void addSparseCandidates(String query, List<PlantEntry> entries,
                                     Map<String, Candidate> candidates) {
        if (sparseIndex == null) return;
        try {
            List<SparseIndexService.SparseHit> hits = sparseIndex.search(
                    KnowledgeSource.PLANT_ENTITY, query, properties.getCandidateTopK());
            double topScore = hits.isEmpty() ? 0 : hits.get(0).score();
            Map<String, PlantEntry> byId = entriesById(entries);
            for (SparseIndexService.SparseHit hit : hits) {
                PlantEntry entry = byId.get(hit.document().canonicalPlantId());
                if (entry != null) {
                    double normalizedScore = topScore <= 0 ? 0 : Math.min(1, hit.score() / topScore);
                    candidate(candidates, entry).sparseScore(normalizedScore);
                }
            }
        } catch (RuntimeException ignored) {
            // The entity vector and character paths can still resolve safely while the local index is rebuilding.
        }
    }

    private void addVectorCandidates(String query, List<PlantEntry> entries,
                                     Map<String, Candidate> candidates) {
        if (entityStore == null) return;
        try {
            SearchRequest request = SearchRequest.builder().query(query)
                    .topK(properties.getCandidateTopK()).similarityThreshold(0).build();
            List<org.springframework.ai.document.Document> hits = entityStore.similaritySearch(request);
            Map<String, PlantEntry> byId = entriesById(entries);
            for (org.springframework.ai.document.Document hit : hits) {
                Object canonicalPlantId = hit.getMetadata().get("canonicalPlantId");
                PlantEntry entry = canonicalPlantId == null ? null : byId.get(canonicalPlantId.toString());
                if (entry != null) {
                    candidate(candidates, entry).vectorScore(hit.getScore() == null ? 0 : hit.getScore());
                }
            }
        } catch (RuntimeException ignored) {
            // Missing or temporarily unavailable entity collections must not break safe rejection.
        }
    }

    private Map<String, PlantEntry> entriesById(List<PlantEntry> entries) {
        Map<String, PlantEntry> result = new LinkedHashMap<>();
        entries.forEach(entry -> result.put(entry.canonicalPlantId(), entry));
        return result;
    }

    private Candidate candidate(Map<String, Candidate> candidates, PlantEntry entry) {
        return candidates.computeIfAbsent(entry.canonicalPlantId(), ignored -> new Candidate(entry));
    }

    private double bestSubstringSimilarity(String query, String name) {
        if (query.isBlank() || name.isBlank()) return 0;
        int minLength = Math.max(1, name.length() - 1);
        int maxLength = Math.min(query.length(), name.length() + 1);
        double best = 0;
        for (int length = minLength; length <= maxLength; length++) {
            for (int start = 0; start + length <= query.length(); start++) {
                String value = query.substring(start, start + length);
                int distance = levenshtein(value, name);
                best = Math.max(best, 1d - (double) distance / Math.max(value.length(), name.length()));
            }
        }
        return best;
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private void recordCandidateCount(int count) {
        if (metrics != null) metrics.recordCandidates("entity_candidates", "plant_entity", count);
    }

    private List<PlantEntry> catalog() {
        List<PlantEntry> value = catalog;
        if (value != null) return value;
        synchronized (this) {
            if (catalog == null) {
                List<KnowledgeRepository.PlantEntityRow> rows = repository.findPlantEntities();
                if (rows.isEmpty()) {
                    rows = repository.findPlants().stream()
                            .map(row -> new KnowledgeRepository.PlantEntityRow(
                                    row.id(), row.scientificName(), row.commonName())).toList();
                }
                catalog = rows.stream().map(row -> {
                    Set<String> names = new LinkedHashSet<>();
                    addName(names, row.commonName());
                    addName(names, row.scientificName());
                    return new PlantEntry(row.id(), Set.copyOf(names));
                }).filter(entry -> !entry.names().isEmpty()).toList();
            }
            return catalog;
        }
    }

    public void refreshCatalog() {
        catalog = null;
    }

    private void addName(Set<String> names, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) names.add(normalized);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private record PlantEntry(String canonicalPlantId, Set<String> names) { }
    private record TypoCandidate(PlantEntry entry, double score) { }
    private record ComparisonMentions(boolean detected, boolean complete) {
        private static ComparisonMentions none() { return new ComparisonMentions(false, false); }
        private static ComparisonMentions completeComparison() { return new ComparisonMentions(true, true); }
    }

    private static final class Candidate {
        private final PlantEntry entry;
        private double characterScore;
        private double sparseScore;
        private double vectorScore;

        private Candidate(PlantEntry entry) { this.entry = entry; }
        private static Candidate empty() { return new Candidate(new PlantEntry("", Set.of())); }
        private PlantEntry entry() { return entry; }
        private double characterScore() { return characterScore; }
        private double sparseScore() { return sparseScore; }
        private double vectorScore() { return vectorScore; }
        private void characterScore(double value) { characterScore = Math.max(characterScore, value); }
        private void sparseScore(double value) { sparseScore = Math.max(sparseScore, value); }
        private void vectorScore(double value) { vectorScore = Math.max(vectorScore, value); }
        private double rankScore() {
            if (vectorScore > 0) return 0.70 * vectorScore + 0.25 * characterScore + 0.05 * sparseScore;
            return Math.max(characterScore, 0.80 * sparseScore);
        }
    }

    public enum ResolutionKind { GENERIC, KNOWN, AMBIGUOUS, UNKNOWN, OUT_OF_DOMAIN }

    public enum ResolutionMethod { EXPLICIT_ID, EXACT_NAME, EDIT_DISTANCE, LEXICAL, VECTOR, HYBRID, NONE }

    public record Resolution(ResolutionKind kind, String canonicalPlantId, List<String> canonicalPlantIds,
                             Set<String> names,
                             ResolutionMethod method, double top1Score, double top2Score,
                             double scoreMargin, int candidateCount, String rejectionReason) {
        public Resolution(ResolutionKind kind, String canonicalPlantId, Set<String> names) {
            this(kind, canonicalPlantId,
                    canonicalPlantId == null || canonicalPlantId.isBlank() ? List.of() : List.of(canonicalPlantId),
                    names, ResolutionMethod.NONE, 0, 0, 0, 0, "");
        }

        static Resolution generic() {
            return rejected(ResolutionKind.GENERIC, "generic_plant_query", 0, 0, 0);
        }
        static Resolution known(PlantEntry entry, ResolutionMethod method,
                                double top1Score, double top2Score, int candidateCount) {
            return known(List.of(entry), method, top1Score, top2Score, candidateCount);
        }
        static Resolution known(List<PlantEntry> entries, ResolutionMethod method,
                                double top1Score, double top2Score, int candidateCount) {
            List<String> ids = entries.stream().map(PlantEntry::canonicalPlantId).distinct().toList();
            Set<String> names = entries.stream().flatMap(entry -> entry.names().stream())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            return new Resolution(ResolutionKind.KNOWN, ids.get(0), ids, Set.copyOf(names), method,
                    top1Score, top2Score, top1Score - top2Score, candidateCount, "");
        }
        public static Resolution forCanonicalPlantId(String canonicalPlantId) {
            return new Resolution(ResolutionKind.KNOWN, canonicalPlantId, List.of(canonicalPlantId), Set.of(),
                    ResolutionMethod.EXPLICIT_ID, 1, 0, 1, 1, "");
        }
        public EntityResolutionDiagnostics diagnostics() {
            return new EntityResolutionDiagnostics(kind.name(), method.name(),
                    canonicalPlantId == null || canonicalPlantId.isBlank() ? null : canonicalPlantId,
                    canonicalPlantIds,
                    top1Score, top2Score, scoreMargin, candidateCount, rejectionReason);
        }
        static Resolution ambiguous(double top1Score, double top2Score, int candidateCount, String reason) {
            return rejected(ResolutionKind.AMBIGUOUS, reason, top1Score, top2Score, candidateCount);
        }
        static Resolution unknown(String reason, double top1Score, double top2Score, int candidateCount) {
            return rejected(ResolutionKind.UNKNOWN, reason, top1Score, top2Score, candidateCount);
        }
        static Resolution outOfDomain() {
            return rejected(ResolutionKind.OUT_OF_DOMAIN, "out_of_plant_domain", 0, 0, 0);
        }
        private static Resolution rejected(ResolutionKind kind, String reason,
                                           double top1Score, double top2Score, int candidateCount) {
            return new Resolution(kind, "", List.of(), Set.of(), ResolutionMethod.NONE, top1Score, top2Score,
                    top1Score - top2Score, candidateCount, reason);
        }
    }
}
