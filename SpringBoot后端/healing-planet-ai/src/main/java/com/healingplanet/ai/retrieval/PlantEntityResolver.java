package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.ingestion.KnowledgeRepository;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PlantEntityResolver {

    private static final Pattern NAMED_CARE_QUERY = Pattern.compile(
            "^\\s*(.+?)\\s*(?:适合|需要|应该|应当|是否|是不是|怎么|如何|多久|多长时间|要不要|可以不可以|耐不耐|对).*$");
    private static final Set<String> CARE_TERMS = Set.of(
            "光照", "阳光", "浇水", "补水", "温度", "湿度", "施肥", "肥料", "土壤", "修剪", "养护", "黄叶", "枯黄", "叶片"
    );
    private static final Set<String> PLANT_DOMAIN_TERMS = Set.of(
            "植物", "绿植", "盆栽", "花卉", "花盆", "花草", "植株", "园艺", "种植", "栽培", "多肉",
            "养花", "养植物", "养绿植", "盆土", "根系", "叶片"
    );
    private static final Set<String> GENERIC_SUBJECTS = Set.of(
            "植物", "什么植物", "哪种植物", "哪些植物", "这种植物", "新手", "室内", "家里", "办公室", "卧室", "宿舍"
    );

    private final KnowledgeRepository repository;
    private volatile List<PlantEntry> catalog;

    public PlantEntityResolver(KnowledgeRepository repository) {
        this.repository = repository;
    }

    public Resolution resolve(RagQuery query) {
        List<PlantEntry> entries = catalog();
        if (query.canonicalPlantId() != null && !query.canonicalPlantId().isBlank()) {
            return entries.stream()
                    .filter(entry -> query.canonicalPlantId().equals(entry.canonicalPlantId()))
                    .findFirst().map(Resolution::known)
                    .orElseGet(Resolution::unknown);
        }

        String normalizedQuery = normalize(query.query());
        PlantEntry matched = entries.stream()
                .filter(entry -> entry.names().stream().anyMatch(normalizedQuery::contains))
                .max(Comparator.comparingInt(entry -> entry.names().stream()
                        .filter(normalizedQuery::contains).mapToInt(String::length).max().orElse(0)))
                .orElse(null);
        if (matched != null) return Resolution.known(matched);
        if (!isPlantDomainQuery(normalizedQuery)) return Resolution.outOfDomain();
        return hasUnknownNamedEntity(query.query()) ? Resolution.unknown() : Resolution.generic();
    }

    public boolean matches(Resolution resolution, KnowledgeDocument document) {
        if (resolution.kind() == ResolutionKind.GENERIC) return true;
        if (resolution.kind() == ResolutionKind.UNKNOWN || resolution.kind() == ResolutionKind.OUT_OF_DOMAIN) return false;
        if (resolution.canonicalPlantId().equals(document.canonicalPlantId())) return true;

        String searchable = normalize(document.plantName() + " " + document.title() + " " + document.content());
        return resolution.names().stream().anyMatch(searchable::contains);
    }

    private boolean hasUnknownNamedEntity(String query) {
        String normalizedQuery = normalize(query);
        if (CARE_TERMS.stream().noneMatch(normalizedQuery::contains)) return false;
        Matcher matcher = NAMED_CARE_QUERY.matcher(query);
        if (!matcher.matches()) return false;
        String subject = normalize(matcher.group(1))
                .replaceFirst("^(请问|想问下|我想问|帮我看看|我的|我这盆|这盆|家里的|一盆)", "");
        return !subject.isBlank() && !GENERIC_SUBJECTS.contains(subject) && subject.length() <= 30;
    }

    private boolean isPlantDomainQuery(String normalizedQuery) {
        return CARE_TERMS.stream().anyMatch(normalizedQuery::contains)
                || PLANT_DOMAIN_TERMS.stream().anyMatch(normalizedQuery::contains);
    }

    private List<PlantEntry> catalog() {
        List<PlantEntry> value = catalog;
        if (value != null) return value;
        synchronized (this) {
            if (catalog == null) {
                catalog = repository.findPlants().stream().map(row -> {
                    Set<String> names = new LinkedHashSet<>();
                    addName(names, row.commonName());
                    addName(names, row.scientificName());
                    return new PlantEntry(row.id(), Set.copyOf(names));
                }).filter(entry -> !entry.names().isEmpty()).toList();
            }
            return catalog;
        }
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

    public enum ResolutionKind { GENERIC, KNOWN, UNKNOWN, OUT_OF_DOMAIN }

    public record Resolution(ResolutionKind kind, String canonicalPlantId, Set<String> names) {
        static Resolution generic() { return new Resolution(ResolutionKind.GENERIC, "", Set.of()); }
        static Resolution known(PlantEntry entry) {
            return new Resolution(ResolutionKind.KNOWN, entry.canonicalPlantId(), entry.names());
        }
        static Resolution unknown() { return new Resolution(ResolutionKind.UNKNOWN, "", Set.of()); }
        static Resolution outOfDomain() { return new Resolution(ResolutionKind.OUT_OF_DOMAIN, "", Set.of()); }
    }
}
