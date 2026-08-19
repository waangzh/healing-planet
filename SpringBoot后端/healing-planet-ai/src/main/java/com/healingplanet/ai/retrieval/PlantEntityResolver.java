package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.ingestion.KnowledgeRepository;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PlantEntityResolver {

    private static final Pattern CARE_ANCHOR = Pattern.compile(
            "浇水|补水|施肥|修剪|光照|阳光|温度|湿度|肥料|土壤|养护|黄叶|发黄|枯黄|叶片|晒|太阳|浇|补|"
                    + "出现|处理|频率|耐阴|喜阴|弱光|强光|直射|状态|异常|判断");
    private static final Pattern LEADING_MENTION_NOISE = Pattern.compile(
            "^(?:请问|想问下|我想问|帮我看看|帮我看下|我的|我这盆|这盆|家里的|一盆|一株|这株)+");
    private static final Pattern LEADING_TIME_CONTEXT = Pattern.compile(
            "^(?:(?:每|一)(?:天|周|星期|个星期|月)|平时|平常)(?:给)?");
    private static final Pattern TRAILING_MENTION_NOISE = Pattern.compile(
            "(?:(?:每|一)(?:天|周|星期|个星期|月)|平时|平常|应该|应当|需要|适合|是否|"
                    + "是不是|怎么|如何|多久|多长时间|在什么情况下|什么情况下|要不要|"
                    + "可以不可以|耐不耐|能不能|不能|能|一直|老|该|什么|的|建议|"
                    + "出现|处理|频率|日常|状态|异常|判断|情况|里|吗)+$");

    private final KnowledgeRepository repository;
    private final QueryRouter queryRouter;
    private final PlantAliasMatcher aliasMatcher;
    private final PlantCandidateGenerator candidateGenerator;
    private final PlantEntityDisambiguator disambiguator;
    private volatile List<PlantCatalogEntry> catalog;

    public PlantEntityResolver(KnowledgeRepository repository) {
        this(repository, new QueryRouter(), new PlantAliasMatcher(),
                new PlantCandidateGenerator(null, null, new RagProperties(), null), null);
    }

    @Autowired
    public PlantEntityResolver(KnowledgeRepository repository,
                               QueryRouter queryRouter, PlantAliasMatcher aliasMatcher,
                               PlantCandidateGenerator candidateGenerator,
                               PlantEntityDisambiguator disambiguator) {
        this.repository = repository;
        this.queryRouter = queryRouter;
        this.aliasMatcher = aliasMatcher;
        this.candidateGenerator = candidateGenerator;
        this.disambiguator = disambiguator;
    }

    PlantEntityResolver(KnowledgeRepository repository,
                        VectorStore entityStore,
                        SparseIndexService sparseIndex,
                        RagProperties ragProperties,
                        RetrievalMetrics metrics) {
        this(repository, entityStore, sparseIndex, ragProperties, metrics, null);
    }

    PlantEntityResolver(KnowledgeRepository repository,
                        VectorStore entityStore,
                        SparseIndexService sparseIndex,
                        RagProperties ragProperties,
                        RetrievalMetrics metrics,
                        PlantEntityDisambiguator disambiguator) {
        this(repository, new QueryRouter(), new PlantAliasMatcher(),
                new PlantCandidateGenerator(entityStore, sparseIndex, ragProperties, metrics), disambiguator);
    }

    public Resolution resolve(RagQuery query) {
        List<PlantCatalogEntry> entries = catalog();
        if (query.canonicalPlantId() != null && !query.canonicalPlantId().isBlank()) {
            return entries.stream()
                    .filter(entry -> query.canonicalPlantId().equals(entry.canonicalPlantId()))
                    .findFirst().map(entry -> Resolution.known(entry, ResolutionMethod.EXPLICIT_ID,
                            1, 0, 1))
                    .orElseGet(() -> Resolution.unknown("canonical_plant_id_not_found", 0, 0, 0));
        }

        String normalizedQuery = normalize(query.query());
        String mentionQuery = stripLeadingMentionNoise(normalizedQuery);
        String namedSubject = extractPotentialMention(query.query());
        PlantAliasMatcher.MatchResult aliasMatch = aliasMatcher.match(
                normalizedQuery, mentionQuery, namedSubject, entries);
        if (aliasMatch.status() == PlantAliasMatcher.MatchStatus.KNOWN) {
            return Resolution.known(aliasMatch.entries(), aliasMatch.alias()
                            ? ResolutionMethod.ALIAS : ResolutionMethod.EXACT_NAME,
                    1, 0, aliasMatch.candidateCount());
        }
        if (aliasMatch.status() == PlantAliasMatcher.MatchStatus.AMBIGUOUS) {
            return Resolution.ambiguous(1, 1, aliasMatch.candidateCount(), aliasMatch.reason());
        }
        if (aliasMatch.status() == PlantAliasMatcher.MatchStatus.UNKNOWN) {
            return Resolution.unknown(aliasMatch.reason(), 1, 0, aliasMatch.candidateCount());
        }

        QueryRouter.RoutingDecision route = queryRouter.route(query);
        boolean catalogNameMentioned = aliasMatcher.containsCatalogName(normalizedQuery, entries);
        if (route.entityRequirement() == QueryRouter.EntityRequirement.OPTIONAL && !catalogNameMentioned) {
            return Resolution.generic();
        }

        boolean plantDomain = route.plantDomain() || catalogNameMentioned;
        List<PlantCandidateGenerator.Candidate> ranked = candidateGenerator.generate(
                query.query(), normalizedQuery, entries, aliasMatcher.contextualEntryIds(normalizedQuery, entries),
                plantDomain);
        String catalogMention = aliasMatcher.catalogMention(normalizedQuery, ranked);
        Resolution llmResolution = resolveWithLlm(query.query(),
                catalogMention.isBlank() ? namedSubject : catalogMention, ranked, plantDomain);
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

    private String stripLeadingMentionNoise(String query) {
        return LEADING_MENTION_NOISE.matcher(query).replaceFirst("");
    }

    private double topScore(List<PlantCandidateGenerator.Candidate> ranked) {
        return ranked.isEmpty() ? 0 : ranked.get(0).vectorScore() > 0
                ? ranked.get(0).vectorScore() : ranked.get(0).characterScore();
    }

    private double secondScore(List<PlantCandidateGenerator.Candidate> ranked) {
        if (ranked.size() < 2) return 0;
        PlantCandidateGenerator.Candidate second = ranked.get(1);
        return ranked.get(0).vectorScore() > 0 ? second.vectorScore() : second.characterScore();
    }

    private Resolution resolveWithLlm(String rawQuery, String namedSubject,
                                      List<PlantCandidateGenerator.Candidate> ranked,
                                      boolean plantDomain) {
        if (!plantDomain || disambiguator == null || ranked.isEmpty()) return null;
        if (namedSubject.isBlank()
                && ranked.stream().noneMatch(PlantCandidateGenerator.Candidate::hasExactCatalogName)) return null;
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

    private double topComparableScore(List<PlantCandidateGenerator.Candidate> ranked,
                                      PlantCandidateGenerator.Candidate selected) {
        return ranked.stream()
                .filter(candidate -> candidate != selected)
                .findFirst()
                .map(candidate -> candidate.vectorScore() > 0 ? candidate.vectorScore() : candidate.characterScore())
                .orElse(0d);
    }

    private List<PlantCatalogEntry> catalog() {
        List<PlantCatalogEntry> value = catalog;
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
                    Set<String> aliases = new LinkedHashSet<>();
                    addName(names, row.commonName());
                    addName(names, row.scientificName());
                    row.aliases().forEach(alias -> {
                        addName(names, alias);
                        addName(aliases, alias);
                    });
                    return new PlantCatalogEntry(row.id(), Set.copyOf(names), Set.copyOf(aliases));
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

    public enum ResolutionKind { GENERIC, KNOWN, AMBIGUOUS, UNKNOWN, OUT_OF_DOMAIN }

    public enum ResolutionMethod { EXPLICIT_ID, EXACT_NAME, ALIAS, EDIT_DISTANCE, LEXICAL, VECTOR, HYBRID, LLM, NONE }

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
        static Resolution known(PlantCatalogEntry entry, ResolutionMethod method,
                                double top1Score, double top2Score, int candidateCount) {
            return known(List.of(entry), method, top1Score, top2Score, candidateCount);
        }
        static Resolution known(List<PlantCatalogEntry> entries, ResolutionMethod method,
                                double top1Score, double top2Score, int candidateCount) {
            List<String> ids = entries.stream().map(PlantCatalogEntry::canonicalPlantId).distinct().toList();
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
