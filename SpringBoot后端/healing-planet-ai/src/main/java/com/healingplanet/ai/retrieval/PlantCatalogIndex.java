package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.ingestion.KnowledgeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared, database-backed plant name and alias index. */
@Component
public class PlantCatalogIndex {
    private final KnowledgeRepository repository;
    private final PlantAliasMatcher aliasMatcher;
    private volatile List<PlantCatalogEntry> catalog;

    @Autowired
    public PlantCatalogIndex(KnowledgeRepository repository, PlantAliasMatcher aliasMatcher) {
        this.repository = repository;
        this.aliasMatcher = aliasMatcher;
    }

    private PlantCatalogIndex() {
        this.repository = null;
        this.aliasMatcher = new PlantAliasMatcher();
        this.catalog = List.of();
    }

    static PlantCatalogIndex empty() {
        return new PlantCatalogIndex();
    }

    boolean containsRegisteredMention(String query) {
        return aliasMatcher.containsCatalogName(normalize(query), entries());
    }

    List<PlantCatalogEntry> entries() {
        List<PlantCatalogEntry> value = catalog;
        if (value != null) return value;
        synchronized (this) {
            if (catalog == null) catalog = load();
            return catalog;
        }
    }

    public void refresh() {
        catalog = repository == null ? List.of() : null;
    }

    private List<PlantCatalogEntry> load() {
        if (repository == null) return List.of();
        List<KnowledgeRepository.PlantEntityRow> rows = repository.findPlantEntities();
        if (rows.isEmpty()) {
            rows = repository.findPlants().stream()
                    .map(row -> new KnowledgeRepository.PlantEntityRow(
                            row.id(), row.scientificName(), row.commonName()))
                    .toList();
        }
        return rows.stream().map(row -> {
            Set<String> names = new LinkedHashSet<>();
            Set<String> aliases = new LinkedHashSet<>();
            addName(names, row.commonName());
            addName(names, row.scientificName());
            row.aliases().forEach(alias -> {
                addName(names, alias);
                addName(aliases, alias);
            });
            String canonicalName = row.commonName() == null || row.commonName().isBlank()
                    ? row.scientificName() : row.commonName();
            return new PlantCatalogEntry(row.id(), canonicalName, Set.copyOf(names), Set.copyOf(aliases));
        }).filter(entry -> !entry.names().isEmpty()).toList();
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
}
