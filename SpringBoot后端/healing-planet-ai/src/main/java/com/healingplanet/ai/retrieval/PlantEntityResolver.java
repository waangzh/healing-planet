package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Closed-set entity linker backed by an immutable catalog and indexed fuzzy lookup. */
@Component
public class PlantEntityResolver {
    private final PlantCatalogIndex catalog;
    private final PlantEntityCandidateRetriever candidateRetriever;
    private final PlantEntityDisambiguator disambiguator;
    private final RagProperties.EntityResolution properties;
    private final UnresolvedPlantMentionDetector unresolvedDetector = new UnresolvedPlantMentionDetector();

    @Autowired
    public PlantEntityResolver(PlantCatalogIndex catalog, PlantEntityCandidateRetriever candidateRetriever,
                               PlantEntityDisambiguator disambiguator, RagProperties ragProperties) {
        this.catalog = catalog;
        this.candidateRetriever = candidateRetriever;
        this.disambiguator = disambiguator;
        this.properties = ragProperties.getEntityResolution();
    }

    PlantEntityResolver(PlantCatalogIndex catalog, PlantEntityCandidateRetriever candidateRetriever,
                        PlantEntityDisambiguator disambiguator) {
        this(catalog, candidateRetriever, disambiguator, new RagProperties());
    }

    public Resolution resolve(RetrievalRequest request) {
        RagQuery query = request.query();
        PlantCatalogSnapshot snapshot = catalog.snapshot();
        String normalized = PlantCatalogIndex.normalize(query.query());
        List<PlantMention> mentions = snapshot.mentionMatcher().find(normalized);
        String explicitId = query.canonicalPlantId() == null ? "" : query.canonicalPlantId().trim();
        if (!explicitId.isBlank()) return resolveExplicit(explicitId, mentions, snapshot);
        if (!mentions.isEmpty()) return resolveMentions(query.query(), normalized, mentions, snapshot);
        if (request.routing().outOfDomain()) return Resolution.outOfDomain();
        if (request.routing().entityRequirement() == QueryRouter.EntityRequirement.OPTIONAL) return Resolution.generic();

        List<PlantEntityCandidateRetriever.Candidate> candidates = candidateRetriever.retrieve(normalized, snapshot);
        return resolveFuzzy(query.query(), candidates);
    }

    private Resolution resolveExplicit(String explicitId, List<PlantMention> mentions, PlantCatalogSnapshot snapshot) {
        PlantCatalogEntry entry = snapshot.byId().get(explicitId);
        if (entry == null) return Resolution.unknown("canonical_plant_id_not_found", 0, 0, 0);
        List<PlantMention> conflicting = mentions.stream()
                .filter(mention -> mention.bindings().stream()
                        .anyMatch(binding -> !explicitId.equals(binding.canonicalPlantId())))
                .toList();
        return !conflicting.isEmpty() ? Resolution.conflict(entry, conflicting)
                : Resolution.known(entry, ResolutionMethod.EXPLICIT_ID,
                1, 0, 1);
    }

    private Resolution resolveMentions(String rawQuery, String normalizedQuery, List<PlantMention> mentions,
                                       PlantCatalogSnapshot snapshot) {
        List<PlantCatalogEntry> resolved = new ArrayList<>();
        Resolution singleCollisionChoice = null;
        boolean llmDisambiguated = false;
        for (PlantMention mention : mentions) {
            List<PlantCatalogEntry> entries = mention.bindings().stream()
                    .map(binding -> snapshot.byId().get(binding.canonicalPlantId())).distinct().toList();
            if (entries.size() == 1) {
                resolved.add(entries.get(0));
                continue;
            }
            List<PlantEntityCandidateRetriever.Candidate> candidates = entries.stream()
                    .map(entry -> new PlantEntityCandidateRetriever.Candidate(entry, mention.text(), 1)).toList();
            Resolution choice = disambiguateCandidates(rawQuery, candidates, "alias_collision");
            if (choice.kind() != ResolutionKind.KNOWN) return choice;
            resolved.addAll(choice.entries(snapshot));
            llmDisambiguated = true;
            if (mentions.size() == 1) singleCollisionChoice = choice;
        }
        List<PlantCatalogEntry> distinct = resolved.stream().distinct().toList();
        PlantNameType type = mentions.stream().flatMap(mention -> mention.bindings().stream())
                .map(PlantNameBinding::type).findFirst().orElse(PlantNameType.COMMON_NAME);
        List<String> unresolved = unresolvedDetector.find(normalizedQuery, mentions, snapshot);
        if (!unresolved.isEmpty()) {
            return Resolution.partial(distinct, unresolved,
                    llmDisambiguated ? ResolutionMethod.LLM : method(type), mentions.get(0).text());
        }
        if (singleCollisionChoice != null) return singleCollisionChoice;
        return Resolution.known(distinct, method(type), 1, 0, distinct.size(), mentions.get(0).text());
    }

    private Resolution resolveFuzzy(String rawQuery, List<PlantEntityCandidateRetriever.Candidate> candidates) {
        if (candidates.isEmpty()) return Resolution.unknown("no_indexed_entity_candidate", 0, 0, 0);
        PlantEntityCandidateRetriever.Candidate top = candidates.get(0);
        double second = candidates.size() < 2 ? 0 : candidates.get(1).score();
        double margin = top.score() - second;
        if (top.score() < properties.getFuzzySoftThreshold()) {
            return Resolution.unknown("fuzzy_score_below_threshold", top.score(), second, candidates.size());
        }
        if (candidates.size() > 1 && margin < properties.getFuzzySoftMargin()) {
            return disambiguateCandidates(rawQuery, candidates, "fuzzy_candidates_too_close");
        }
        PlantScope scope = top.score() >= properties.getFuzzyHardThreshold()
                && (candidates.size() == 1 || margin >= properties.getFuzzyHardMargin())
                ? PlantScope.hard(List.of(top.entry().canonicalPlantId()))
                : PlantScope.soft(List.of(top.entry().canonicalPlantId()));
        return Resolution.fuzzy(top.entry(), top.mention(), top.score(), second, candidates.size(), scope);
    }

    private Resolution disambiguateCandidates(String rawQuery, List<PlantEntityCandidateRetriever.Candidate> candidates,
                                              String reason) {
        double top = candidates.isEmpty() ? 0 : candidates.get(0).score();
        double second = candidates.size() < 2 ? 0 : candidates.get(1).score();
        if (disambiguator == null) return Resolution.ambiguous(top, second, candidates.size(), reason);
        List<PlantEntityDisambiguator.CandidateOption> options = candidates.stream()
                .map(candidate -> new PlantEntityDisambiguator.CandidateOption(candidate.entry().canonicalPlantId(),
                        candidate.entry().names(), 0, candidate.score())).toList();
        PlantEntityDisambiguator.Decision decision = disambiguator.disambiguate(rawQuery,
                candidates.isEmpty() ? "" : candidates.get(0).mention(), options);
        if (decision == null || !decision.attempted() || decision.unavailable() || decision.ambiguous()) {
            return Resolution.ambiguous(top, second, candidates.size(), decision == null ? reason : decision.reason());
        }
        if (!decision.known()) return Resolution.unknown(decision.reason(), top, second, candidates.size());
        return candidates.stream().filter(candidate -> decision.canonicalPlantId()
                        .equals(candidate.entry().canonicalPlantId())).findFirst()
                .map(candidate -> Resolution.known(List.of(candidate.entry()), ResolutionMethod.LLM,
                        decision.confidence(), second, candidates.size(), candidate.mention()))
                .orElseGet(() -> Resolution.ambiguous(top, second, candidates.size(), "llm_invalid_candidate"));
    }

    public boolean matches(Resolution resolution, KnowledgeDocument document) {
        return !resolution.scope().filtersPlantKnowledge()
                || resolution.canonicalPlantIds().contains(document.canonicalPlantId());
    }

    private ResolutionMethod method(PlantNameType type) {
        return switch (type) {
            case ALIAS -> ResolutionMethod.ALIAS;
            case SCIENTIFIC_NAME -> ResolutionMethod.SCIENTIFIC_NAME;
            case COMMON_NAME -> ResolutionMethod.EXACT_NAME;
        };
    }

    public enum ResolutionKind { GENERIC, KNOWN, PARTIAL, AMBIGUOUS, UNKNOWN, OUT_OF_DOMAIN, CONFLICT }
    public enum ResolutionMethod { EXPLICIT_ID, EXACT_NAME, SCIENTIFIC_NAME, ALIAS, FUZZY, LLM, NONE }

    public record Resolution(ResolutionKind kind, String canonicalPlantId, List<String> canonicalPlantIds,
                             Set<String> names, ResolutionMethod method, double top1Score, double top2Score,
                             double scoreMargin, int candidateCount, String rejectionReason,
                             List<EntityResolutionDiagnostics.AliasNormalization> aliasNormalizations,
                             List<String> unresolvedMentions, List<String> conflictingMentions,
                             PlantScope scope) {
        public Resolution {
            canonicalPlantIds = canonicalPlantIds == null ? List.of() : List.copyOf(canonicalPlantIds);
            names = names == null ? Set.of() : Set.copyOf(names);
            aliasNormalizations = aliasNormalizations == null ? List.of() : List.copyOf(aliasNormalizations);
            unresolvedMentions = unresolvedMentions == null ? List.of() : List.copyOf(unresolvedMentions);
            conflictingMentions = conflictingMentions == null ? List.of() : List.copyOf(conflictingMentions);
            scope = scope == null ? scopeFor(kind, canonicalPlantIds) : scope;
        }

        public Resolution(ResolutionKind kind, String canonicalPlantId, Set<String> names) {
            this(kind, canonicalPlantId, canonicalPlantId == null || canonicalPlantId.isBlank() ? List.of()
                    : List.of(canonicalPlantId), names, ResolutionMethod.NONE, 0, 0, 0, 0, "", List.of(),
                    List.of(), List.of(), null);
        }
        public Resolution(ResolutionKind kind, String canonicalPlantId, List<String> canonicalPlantIds,
                          Set<String> names, ResolutionMethod method, double top1Score, double top2Score,
                          double scoreMargin, int candidateCount, String rejectionReason) {
            this(kind, canonicalPlantId, canonicalPlantIds, names, method, top1Score, top2Score, scoreMargin,
                    candidateCount, rejectionReason, List.of(), List.of(), List.of(), null);
        }
        public Resolution(ResolutionKind kind, String canonicalPlantId, List<String> canonicalPlantIds,
                          Set<String> names, ResolutionMethod method, double top1Score, double top2Score,
                          double scoreMargin, int candidateCount, String rejectionReason,
                          List<EntityResolutionDiagnostics.AliasNormalization> aliasNormalizations,
                          List<String> unresolvedMentions) {
            this(kind, canonicalPlantId, canonicalPlantIds, names, method, top1Score, top2Score, scoreMargin,
                    candidateCount, rejectionReason, aliasNormalizations, unresolvedMentions, List.of(), null);
        }
        public boolean hasResolvedEntities() { return !canonicalPlantIds.isEmpty(); }
        static Resolution known(PlantCatalogEntry entry, ResolutionMethod method, double top1, double top2, int count) {
            return known(List.of(entry), method, top1, top2, count, "");
        }
        static Resolution known(List<PlantCatalogEntry> entries, ResolutionMethod method,
                                double top1, double top2, int count, String matchedName) {
            List<String> ids = entries.stream().map(PlantCatalogEntry::canonicalPlantId).distinct().toList();
            Set<String> names = entries.stream().flatMap(entry -> entry.names().stream())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            List<EntityResolutionDiagnostics.AliasNormalization> aliases = method == ResolutionMethod.ALIAS
                    ? entries.stream().map(entry -> new EntityResolutionDiagnostics.AliasNormalization(matchedName,
                    entry.canonicalPlantId(), entry.canonicalPlantName())).toList() : List.of();
            return new Resolution(ResolutionKind.KNOWN, ids.get(0), ids, names, method, top1, top2,
                    top1 - top2, count, "", aliases, List.of(), List.of(), PlantScope.hard(ids));
        }
        static Resolution fuzzy(PlantCatalogEntry entry, String mention, double top1, double top2,
                                int count, PlantScope scope) {
            return new Resolution(ResolutionKind.KNOWN, entry.canonicalPlantId(), List.of(entry.canonicalPlantId()),
                    entry.names(), ResolutionMethod.FUZZY, top1, top2, top1 - top2, count, "", List.of(),
                    List.of(), List.of(), scope);
        }
        static Resolution partial(List<PlantCatalogEntry> entries, List<String> unresolved,
                                  ResolutionMethod method, String matchedName) {
            List<String> ids = entries.stream().map(PlantCatalogEntry::canonicalPlantId).distinct().toList();
            Set<String> names = entries.stream().flatMap(entry -> entry.names().stream())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            List<EntityResolutionDiagnostics.AliasNormalization> aliases = method == ResolutionMethod.ALIAS
                    ? entries.stream().map(entry -> new EntityResolutionDiagnostics.AliasNormalization(matchedName,
                    entry.canonicalPlantId(), entry.canonicalPlantName())).toList() : List.of();
            return new Resolution(ResolutionKind.PARTIAL, ids.get(0), ids, names, method, 1, 0, 1,
                    ids.size(), "partial_entity_unresolved", aliases, unresolved, List.of(), PlantScope.hard(ids));
        }
        static Resolution conflict(PlantCatalogEntry explicit, List<PlantMention> mentions) {
            return new Resolution(ResolutionKind.CONFLICT, explicit.canonicalPlantId(), List.of(explicit.canonicalPlantId()),
                    explicit.names(), ResolutionMethod.EXPLICIT_ID, 1, 0, 1, 1,
                    "explicit_canonical_plant_id_conflicts_with_query_mention", List.of(), List.of(),
                    mentions.stream().map(PlantMention::text).toList(),
                    PlantScope.conflict(List.of(explicit.canonicalPlantId())));
        }
        static Resolution generic() { return rejected(ResolutionKind.GENERIC, "generic_plant_query", 0, 0, 0); }
        static Resolution ambiguous(double top1, double top2, int count, String reason) {
            return rejected(ResolutionKind.AMBIGUOUS, reason, top1, top2, count);
        }
        static Resolution unknown(String reason, double top1, double top2, int count) {
            return rejected(ResolutionKind.UNKNOWN, reason, top1, top2, count);
        }
        static Resolution outOfDomain() { return rejected(ResolutionKind.OUT_OF_DOMAIN, "out_of_plant_domain", 0, 0, 0); }
        public static Resolution forCanonicalPlantId(String id) {
            return new Resolution(ResolutionKind.KNOWN, id, List.of(id), Set.of(), ResolutionMethod.EXPLICIT_ID,
                    1, 0, 1, 1, "", List.of(), List.of(), List.of(), PlantScope.hard(List.of(id)));
        }
        List<PlantCatalogEntry> entries(PlantCatalogSnapshot snapshot) {
            return canonicalPlantIds.stream().map(snapshot.byId()::get).filter(java.util.Objects::nonNull).toList();
        }
        public EntityResolutionDiagnostics diagnostics() {
            return new EntityResolutionDiagnostics(kind.name(), method.name(), canonicalPlantId == null || canonicalPlantId.isBlank()
                    ? null : canonicalPlantId, canonicalPlantIds, top1Score, top2Score, scoreMargin, candidateCount,
                    rejectionReason, aliasNormalizations, unresolvedMentions, conflictingMentions, scope.kind().name());
        }
        private static Resolution rejected(ResolutionKind kind, String reason, double top1, double top2, int count) {
            return new Resolution(kind, "", List.of(), Set.of(), ResolutionMethod.NONE, top1, top2, top1 - top2,
                    count, reason, List.of(), List.of(), List.of(), scopeFor(kind, List.of()));
        }
        private static PlantScope scopeFor(ResolutionKind kind, List<String> ids) {
            return kind == ResolutionKind.KNOWN || kind == ResolutionKind.PARTIAL ? PlantScope.hard(ids)
                    : kind == ResolutionKind.CONFLICT ? PlantScope.conflict(ids) : PlantScope.none();
        }
    }
}
