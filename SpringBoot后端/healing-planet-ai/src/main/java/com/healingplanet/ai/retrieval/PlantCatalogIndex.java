package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.ingestion.KnowledgeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared, database-backed immutable catalog snapshot. */
@Component
public class PlantCatalogIndex {
    private final KnowledgeRepository repository;
    private volatile PlantCatalogSnapshot snapshot;

    @Autowired
    public PlantCatalogIndex(KnowledgeRepository repository) {
        this.repository = repository;
    }

    private PlantCatalogIndex() {
        this.repository = null;
        this.snapshot = PlantCatalogSnapshot.empty();
    }

    static PlantCatalogIndex empty() {
        return new PlantCatalogIndex();
    }

    PlantCatalogSnapshot snapshot() {
        PlantCatalogSnapshot value = snapshot;
        if (value != null) return value;
        synchronized (this) {
            if (snapshot == null) snapshot = load();
            return snapshot;
        }
    }

    public void refresh() {
        snapshot = repository == null ? PlantCatalogSnapshot.empty() : load();
    }

    /**
     * Maps explicit post tags and mentions to catalog plants without filtering retrieval.
     * Ambiguous aliases and one-character free-text mentions deliberately receive no credit.
     */
    public CommunityPlantResolution resolveCommunityPlants(String title, String content, List<String> tags) {
        PlantCatalogSnapshot catalog = snapshot();
        Map<String, Double> confidenceById = new LinkedHashMap<>();
        for (String tag : tags == null ? List.<String>of() : tags) {
            List<PlantNameBinding> bindings = catalog.byNormalizedName().get(normalize(tag));
            addUnambiguous(bindings, 1d, confidenceById);
        }
        addMentions(catalog, title, 0.85d, confidenceById);
        addMentions(catalog, content, 0.65d, confidenceById);
        List<String> ids = List.copyOf(confidenceById.keySet());
        String primaryName = ids.size() == 1 && catalog.byId().containsKey(ids.get(0))
                ? catalog.byId().get(ids.get(0)).canonicalPlantName() : "";
        double confidence = confidenceById.values().stream().mapToDouble(Double::doubleValue).max().orElse(0d);
        return new CommunityPlantResolution(ids, primaryName, confidence);
    }

    public String canonicalPlantName(String canonicalPlantId) {
        PlantCatalogEntry entry = snapshot().byId().get(canonicalPlantId);
        return entry == null ? "" : entry.canonicalPlantName();
    }

    private void addMentions(PlantCatalogSnapshot catalog, String value, double confidence,
                             Map<String, Double> confidenceById) {
        for (PlantMention mention : catalog.mentionMatcher().find(normalize(value))) {
            if (mention.text().length() < 2) continue;
            addUnambiguous(mention.bindings(), confidence, confidenceById);
        }
    }

    private void addUnambiguous(List<PlantNameBinding> bindings, double confidence,
                                Map<String, Double> confidenceById) {
        if (bindings == null || bindings.isEmpty()) return;
        Set<String> ids = bindings.stream().map(PlantNameBinding::canonicalPlantId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (ids.size() != 1) return;
        String id = ids.iterator().next();
        confidenceById.merge(id, confidence, Math::max);
    }

    private PlantCatalogSnapshot load() {
        if (repository == null) return PlantCatalogSnapshot.empty();
        List<KnowledgeRepository.PlantEntityRow> rows = repository.findPlantEntities();
        if (rows.isEmpty()) {
            rows = repository.findPlants().stream()
                    .map(row -> new KnowledgeRepository.PlantEntityRow(
                            row.id(), row.scientificName(), row.commonName()))
                    .toList();
        }
        Map<String, PlantCatalogEntry> byId = new LinkedHashMap<>();
        Map<String, List<PlantNameBinding>> byName = new LinkedHashMap<>();
        for (KnowledgeRepository.PlantEntityRow row : rows) {
            Set<String> names = new LinkedHashSet<>();
            Set<String> aliases = new LinkedHashSet<>();
            Map<String, PlantNameType> nameTypes = new LinkedHashMap<>();
            addName(names, nameTypes, row.commonName(), PlantNameType.COMMON_NAME);
            addName(names, nameTypes, row.scientificName(), PlantNameType.SCIENTIFIC_NAME);
            row.aliases().forEach(alias -> {
                String normalized = normalize(alias);
                addName(names, nameTypes, alias, PlantNameType.ALIAS);
                if (!normalized.isBlank()) aliases.add(normalized);
            });
            String canonicalName = row.commonName() == null || row.commonName().isBlank()
                    ? row.scientificName() : row.commonName();
            if (names.isEmpty()) continue;
            PlantCatalogEntry entry = new PlantCatalogEntry(row.id(), canonicalName, Set.copyOf(names),
                    Set.copyOf(aliases), Map.copyOf(nameTypes));
            byId.put(entry.canonicalPlantId(), entry);
            nameTypes.forEach((name, type) -> byName.computeIfAbsent(name, ignored -> new ArrayList<>())
                    .add(new PlantNameBinding(entry.canonicalPlantId(), name, type)));
        }
        Map<String, List<PlantNameBinding>> immutableByName = new LinkedHashMap<>();
        byName.forEach((name, bindings) -> immutableByName.put(name, List.copyOf(bindings)));
        int maxNameLength = immutableByName.keySet().stream().mapToInt(String::length).max().orElse(0);
        return new PlantCatalogSnapshot(Map.copyOf(byId), Map.copyOf(immutableByName),
                PlantMentionMatcher.build(immutableByName), maxNameLength);
    }

    private void addName(Set<String> names, Map<String, PlantNameType> types, String value, PlantNameType type) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            names.add(normalized);
            types.putIfAbsent(normalized, type);
        }
    }

    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public record CommunityPlantResolution(List<String> resolvedPlantIds, String primaryPlantName,
                                           double confidence) {
        public CommunityPlantResolution {
            resolvedPlantIds = resolvedPlantIds == null ? List.of() : List.copyOf(resolvedPlantIds);
            primaryPlantName = primaryPlantName == null ? "" : primaryPlantName;
            confidence = Math.max(0d, Math.min(1d, confidence));
        }

        public static CommunityPlantResolution empty() {
            return new CommunityPlantResolution(List.of(), "", 0d);
        }
    }
}
