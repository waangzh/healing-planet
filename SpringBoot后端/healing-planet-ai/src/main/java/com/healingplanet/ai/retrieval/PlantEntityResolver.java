package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.QueryIntent;
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
            "浇水|补水|施肥|修剪|光照|阳光|温度|湿度|肥料|土壤|养护|黄叶|发黄|枯黄|叶片|晒|太阳|浇|补|"
                    + "建议|官方|经验|社区|出现|处理|频率|耐阴|喜阴|弱光|强光|直射|状态|异常|判断");
    private static final Pattern GENERIC_PLANT_QUERY = Pattern.compile(
            "^(?:请问|想问下|我想问|请|帮我)?(?:"
                    + "(?:什么|哪种|哪些|哪类|有哪些).*?(?:植物|绿植|盆栽|花卉)|"
                    + "(?:这种)?(?:植物|绿植|盆栽|花卉)(?:有哪些|推荐|比较好|的|叶|怎么|如何|适合|需要)|"
                    + "(?:适合|推荐).*(?:宿舍|室内|办公室|卧室|家里).*(?:植物|绿植|盆栽|花卉)|"
                    + "(?:宿舍|室内|办公室|卧室|家里).*(?:植物|绿植|盆栽|花卉)(?:有哪些|推荐|比较好))");
    private static final Pattern GENERIC_CARE_CONCEPT_QUERY = Pattern.compile(
            ".*(?:耐阴|喜阴|弱光|强光|直射|光照|浇水|补水|状态|异常).*(?:等于|区别|一样|相同|是什么意思|什么叫).*");
    private static final Pattern LEADING_MENTION_NOISE = Pattern.compile(
            "^(?:请问|想问下|我想问|帮我看看|帮我看下|我的|我这盆|这盆|家里的|一盆|一株|这株|"
                    + "社区里有没有|社区里有没|社区里是否有|社区里的|社区经验里|社区用户遇到|"
                    + "大家分享的|帮我找一篇网友写的|网友写的|网友分享的|这篇社区经验中)+");
    private static final Pattern LEADING_TIME_CONTEXT = Pattern.compile(
            "^(?:(?:每|一)(?:天|周|星期|个星期|月)|平时|平常)(?:给)?");
    private static final Pattern TRAILING_MENTION_NOISE = Pattern.compile(
            "(?:(?:每|一)(?:天|周|星期|个星期|月)|平时|平常|应该|应当|需要|适合|是否|"
                    + "是不是|怎么|如何|多久|多长时间|在什么情况下|什么情况下|要不要|"
                    + "可以不可以|耐不耐|能不能|不能|能|一直|老|该|什么|的|建议|官方|经验|社区|"
                    + "出现|处理|频率|日常|状态|异常|判断|情况|里|吗)+$");
    private static final Pattern COMPARISON_SEPARATOR = Pattern.compile("[和与跟]");
    private static final Pattern COMPARISON_TERM = Pattern.compile("和|与|跟|比较|对比|相比|是否相同|一样|相同");
    private static final Pattern HAN_NAME = Pattern.compile("[\\p{IsHan}]+");
    private static final Set<String> CARE_TERMS = Set.of(
            "光照", "阳光", "浇水", "补水", "温度", "湿度", "施肥", "肥料", "土壤", "修剪", "养护", "黄叶", "发黄", "枯黄", "叶片", "叶子",
            "耐阴", "喜阴", "弱光", "强光", "直射", "状态", "异常", "判断", "处理"
    );
    private static final Set<String> PLANT_DOMAIN_TERMS = Set.of(
            "植物", "绿植", "盆栽", "花卉", "花盆", "花草", "植株", "园艺", "种植", "栽培", "多肉",
            "养花", "养植物", "养绿植", "盆土", "根系", "叶片", "社区", "经验", "状态"
    );

    private final KnowledgeRepository repository;
    private final VectorStore entityStore;
    private final SparseIndexService sparseIndex;
    private final RagProperties.EntityResolution properties;
    private final RetrievalMetrics metrics;
    private final PlantEntityDisambiguator disambiguator;
    private volatile List<PlantEntry> catalog;

    public PlantEntityResolver(KnowledgeRepository repository) {
        this(repository, null, null, new RagProperties(), null, null);
    }

    @Autowired
    public PlantEntityResolver(KnowledgeRepository repository,
                               @Qualifier("plantEntityVectorStore") VectorStore entityStore,
                               SparseIndexService sparseIndex, RagProperties ragProperties,
                               RetrievalMetrics metrics, PlantEntityDisambiguator disambiguator) {
        this.repository = repository;
        this.entityStore = entityStore;
        this.sparseIndex = sparseIndex;
        this.properties = ragProperties.getEntityResolution();
        this.metrics = metrics;
        this.disambiguator = disambiguator;
    }

    PlantEntityResolver(KnowledgeRepository repository,
                        VectorStore entityStore,
                        SparseIndexService sparseIndex,
                        RagProperties ragProperties,
                        RetrievalMetrics metrics) {
        this(repository, entityStore, sparseIndex, ragProperties, metrics, null);
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
        String namedSubject = extractPotentialMention(query.query());
        ComparisonMentions comparison = comparisonMentions(normalizedQuery, entries);
        if (comparison.detected() && !comparison.complete()) {
            return Resolution.unknown("comparison_entity_unresolved", 1, 0, comparison.entries().size());
        }
        if (comparison.complete()) {
            return Resolution.known(comparison.entries(), ResolutionMethod.EXACT_NAME,
                    1, 0, comparison.entries().size());
        }
        List<PlantEntry> subjectMatches = exactNameMatches(namedSubject, entries);
        if (subjectMatches.size() == 1) {
            return Resolution.known(subjectMatches, ResolutionMethod.EXACT_NAME, 1, 0, subjectMatches.size());
        }

        List<PlantEntry> exactMatches = entries.stream()
                .filter(entry -> entry.names().stream().anyMatch(name -> hasStandaloneName(normalizedQuery, name)))
                .toList();
        if (exactMatches.size() == 1) {
            return Resolution.known(exactMatches, ResolutionMethod.EXACT_NAME, 1, 0, exactMatches.size());
        }
        List<PlantEntry> leadingNameMatches = leadingNameMatches(normalizedQuery, entries);
        if (leadingNameMatches.size() == 1) {
            return Resolution.known(leadingNameMatches, ResolutionMethod.EXACT_NAME,
                    1, 0, leadingNameMatches.size());
        }
        boolean catalogNameMentioned = entries.stream()
                .anyMatch(entry -> entry.names().stream().anyMatch(normalizedQuery::contains));
        if (isExplicitGenericPlantQuery(normalizedQuery) && !catalogNameMentioned) return Resolution.generic();

        boolean plantDomain = query.intent() == QueryIntent.COMMUNITY_SEARCH || isPlantDomainQuery(normalizedQuery)
                || catalogNameMentioned;

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        addDirectMentionCandidates(normalizedQuery, entries, candidates);
        addCharacterCandidates(normalizedQuery, entries, candidates);
        if (plantDomain || !candidates.isEmpty()) {
            addSparseCandidates(query.query(), entries, candidates);
            addVectorCandidates(query.query(), entries, candidates);
        }

        List<Candidate> ranked = candidates.values().stream()
                .sorted(Comparator.comparing(Candidate::directMention).reversed()
                        .thenComparing(Comparator.comparingDouble(Candidate::rankScore).reversed()))
                .limit(candidateLimit())
                .toList();
        recordCandidateCount(ranked.size());
        Resolution llmResolution = resolveWithLlm(query.query(), namedSubject, ranked, plantDomain);
        if (llmResolution != null) return llmResolution;

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
        return GENERIC_PLANT_QUERY.matcher(normalizedQuery).find()
                || GENERIC_CARE_CONCEPT_QUERY.matcher(normalizedQuery).matches();
    }

    private String extractPotentialMention(String query) {
        String normalized = normalize(query).replaceAll("[？?。，,;；！!]", "");
        Matcher anchor = CARE_ANCHOR.matcher(normalized);
        while (anchor.find()) {
            String candidate = normalized.substring(0, anchor.start());
            candidate = LEADING_MENTION_NOISE.matcher(candidate).replaceFirst("");
            candidate = LEADING_TIME_CONTEXT.matcher(candidate).replaceFirst("");
            candidate = candidate.replaceFirst("^给", "");
            candidate = TRAILING_MENTION_NOISE.matcher(candidate).replaceFirst("");
            if (!candidate.isBlank() && candidate.length() <= 30) return candidate;
        }
        return "";
    }

    private List<PlantEntry> exactNameMatches(String mention, List<PlantEntry> entries) {
        if (mention == null || mention.isBlank()) return List.of();
        return entries.stream()
                .filter(entry -> entry.names().stream().anyMatch(name -> name.equals(mention)))
                .toList();
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
        boolean leftBoundary = start == 0 || !isHan(query.charAt(start - 1));
        boolean rightBoundary = end == query.length() || !isHan(query.charAt(end));
        return leftBoundary && rightBoundary;
    }

    private List<PlantEntry> leadingNameMatches(String query, List<PlantEntry> entries) {
        int longestName = entries.stream()
                .flatMap(entry -> entry.names().stream())
                .filter(query::startsWith)
                .mapToInt(String::length)
                .max()
                .orElse(0);
        if (longestName == 0) return List.of();
        return entries.stream()
                .filter(entry -> entry.names().stream()
                        .anyMatch(name -> name.length() == longestName && query.startsWith(name)))
                .toList();
    }

    private boolean isHan(char value) {
        return Character.UnicodeScript.of(value) == Character.UnicodeScript.HAN;
    }

    private boolean isAsciiLetterOrDigit(char value) {
        return value >= 'a' && value <= 'z' || value >= '0' && value <= '9';
    }

    private ComparisonMentions comparisonMentions(String query, List<PlantEntry> entries) {
        if (!COMPARISON_TERM.matcher(query).find()) return ComparisonMentions.none();
        Matcher separator = COMPARISON_SEPARATOR.matcher(query);
        if (!separator.find()) return ComparisonMentions.none();

        String left = comparisonSubject(query.substring(0, separator.start()), false);
        String right = comparisonSubject(query.substring(separator.end()), true);
        PlantEntry leftEntry = comparisonEntry(left, entries);
        PlantEntry rightEntry = comparisonEntry(right, entries);
        boolean leftKnown = leftEntry != null;
        boolean rightKnown = rightEntry != null;
        boolean leftMention = leftKnown || looksLikeUnknownMention(left, false);
        boolean rightMention = rightKnown || looksLikeUnknownMention(right, true);
        boolean detected = (leftKnown || rightKnown) && leftMention && rightMention;
        if (!detected) return ComparisonMentions.none();
        List<PlantEntry> comparisonEntries = new ArrayList<>();
        if (leftEntry != null) comparisonEntries.add(leftEntry);
        if (rightEntry != null) comparisonEntries.add(rightEntry);
        return new ComparisonMentions(true, leftKnown && rightKnown, List.copyOf(comparisonEntries));
    }

    private String comparisonSubject(String value, boolean rightSide) {
        String subject = value.replaceFirst("^(请问|想问下|我想问|比较|对比)", "");
        if (rightSide) {
            int possessive = subject.indexOf('的');
            if (possessive > 0) subject = subject.substring(0, possessive);
        }
        return subject.replaceAll("[？?。，,;；]", "");
    }

    private PlantEntry comparisonEntry(String mention, List<PlantEntry> entries) {
        return entries.stream()
                .filter(entry -> entry.names().stream().anyMatch(name -> name.equals(mention)))
                .findFirst()
                .orElse(null);
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

    private Resolution resolveWithLlm(String rawQuery, String namedSubject, List<Candidate> ranked,
                                      boolean plantDomain) {
        if (!plantDomain || disambiguator == null || ranked.isEmpty()) return null;
        if (namedSubject.isBlank() && ranked.stream().noneMatch(Candidate::directMention)) return null;
        List<PlantEntityDisambiguator.CandidateOption> options = ranked.stream()
                .map(candidate -> new PlantEntityDisambiguator.CandidateOption(
                        candidate.entry().canonicalPlantId(),
                        candidate.entry().names(),
                        candidate.vectorScore(),
                        Math.max(candidate.characterScore(), candidate.sparseScore())))
                .toList();
        PlantEntityDisambiguator.Decision decision = disambiguator.disambiguate(rawQuery, namedSubject, options);
        if (decision == null) {
            return Resolution.unknown("llm_empty_response", topScore(ranked), secondScore(ranked), ranked.size());
        }
        if (!decision.attempted()) return null;
        if (!decision.known()) {
            return Resolution.unknown(decision.reason(), topScore(ranked), secondScore(ranked), ranked.size());
        }
        return ranked.stream()
                .filter(candidate -> candidate.entry().canonicalPlantId().equals(decision.canonicalPlantId()))
                .findFirst()
                .map(candidate -> Resolution.known(candidate.entry(), ResolutionMethod.LLM,
                        decision.confidence(), ranked.size() > 1 ? topComparableScore(ranked, candidate) : 0, ranked.size()))
                .orElse(null);
    }

    private double topComparableScore(List<Candidate> ranked, Candidate selected) {
        return ranked.stream()
                .filter(candidate -> candidate != selected)
                .findFirst()
                .map(candidate -> candidate.vectorScore() > 0 ? candidate.vectorScore() : candidate.characterScore())
                .orElse(0d);
    }

    private void addCharacterCandidates(String query, List<PlantEntry> entries,
                                        Map<String, Candidate> candidates) {
        for (PlantEntry entry : entries) {
            double score = entry.names().stream()
                    .mapToDouble(name -> bestSubstringSimilarity(query, name)).max().orElse(0);
            if (score > 0) {
                candidate(candidates, entry).characterScore(score);
            }
        }
    }

    private void addDirectMentionCandidates(String query, List<PlantEntry> entries,
                                            Map<String, Candidate> candidates) {
        for (PlantEntry entry : entries) {
            if (entry.names().stream().anyMatch(query::contains)) {
                candidate(candidates, entry).markDirectMention();
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
            List<org.springframework.ai.document.Document> hits = metrics == null
                    ? entityStore.similaritySearch(request)
                    : metrics.time("embedding", "plant_entity", () -> entityStore.similaritySearch(request));
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

    private int candidateLimit() {
        return Math.max(1, Math.max(properties.getCandidateTopK(), properties.getLlmMaxCandidates()));
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
    private record ComparisonMentions(boolean detected, boolean complete, List<PlantEntry> entries) {
        private static ComparisonMentions none() { return new ComparisonMentions(false, false, List.of()); }
    }

    private static final class Candidate {
        private final PlantEntry entry;
        private double characterScore;
        private double sparseScore;
        private double vectorScore;
        private boolean directMention;

        private Candidate(PlantEntry entry) { this.entry = entry; }
        private PlantEntry entry() { return entry; }
        private double characterScore() { return characterScore; }
        private double sparseScore() { return sparseScore; }
        private double vectorScore() { return vectorScore; }
        private boolean directMention() { return directMention; }
        private void characterScore(double value) { characterScore = Math.max(characterScore, value); }
        private void sparseScore(double value) { sparseScore = Math.max(sparseScore, value); }
        private void vectorScore(double value) { vectorScore = Math.max(vectorScore, value); }
        private void markDirectMention() { directMention = true; }
        private double rankScore() {
            if (vectorScore > 0) return 0.70 * vectorScore + 0.25 * characterScore + 0.05 * sparseScore;
            return Math.max(characterScore, 0.80 * sparseScore);
        }
    }

    public enum ResolutionKind { GENERIC, KNOWN, AMBIGUOUS, UNKNOWN, OUT_OF_DOMAIN }

    public enum ResolutionMethod { EXPLICIT_ID, EXACT_NAME, EDIT_DISTANCE, LEXICAL, VECTOR, HYBRID, LLM, NONE }

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
