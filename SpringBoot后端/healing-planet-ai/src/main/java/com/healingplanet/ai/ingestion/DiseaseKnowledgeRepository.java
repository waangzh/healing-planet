package com.healingplanet.ai.ingestion;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class DiseaseKnowledgeRepository {
    private final JdbcClient jdbcClient;

    public DiseaseKnowledgeRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<DiseaseRow> findAll() {
        return jdbcClient.sql(selectSql() + " order by id")
                .query((rs, rowNum) -> map(rs)).list();
    }

    public List<DiseaseRow> findAfter(String lastId, int limit) {
        return jdbcClient.sql(selectSql() + " where id > :lastId order by id limit :limit")
                .param("lastId", lastId).param("limit", limit)
                .query((rs, rowNum) -> map(rs)).list();
    }

    public DiseaseRow findById(String id) {
        return jdbcClient.sql(selectSql() + " where id = :id")
                .param("id", id).query((rs, rowNum) -> map(rs)).optional().orElse(null);
    }

    private String selectSql() {
        return """
                select id, canonical_plant_id, plant_name, disease_name, aliases, symptoms,
                       visual_symptoms, trigger_conditions, environment_conditions,
                       treatment, prevention, source, source_level, created_at, updated_at
                from plant_disease_knowledge
                """;
    }

    private DiseaseRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DiseaseRow(rs.getString("id"), rs.getString("canonical_plant_id"),
                rs.getString("plant_name"), rs.getString("disease_name"), rs.getString("aliases"),
                rs.getString("symptoms"), rs.getString("visual_symptoms"),
                rs.getString("trigger_conditions"), rs.getString("environment_conditions"),
                rs.getString("treatment"), rs.getString("prevention"), rs.getString("source"),
                rs.getString("source_level"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private Instant instant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record DiseaseRow(String id, String canonicalPlantId, String plantName, String diseaseName,
                             String aliases, String symptoms, String visualSymptoms,
                             String triggerConditions, String environmentConditions,
                             String treatment, String prevention, String source, String sourceLevel,
                             Instant createdAt, Instant updatedAt) {
        public DiseaseRow(String id, String canonicalPlantId, String plantName, String diseaseName,
                          String aliases, String symptoms, String visualSymptoms,
                          String triggerConditions, String environmentConditions,
                          String treatment, String prevention, String source, String sourceLevel) {
            this(id, canonicalPlantId, plantName, diseaseName, aliases, symptoms, visualSymptoms,
                    triggerConditions, environmentConditions, treatment, prevention, source, sourceLevel,
                    null, null);
        }
    }
}
