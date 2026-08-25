package com.healingplanet.ai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
class RagConfigRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<RagConfigRevision> rowMapper = (rs, rowNum) -> new RagConfigRevision(
            rs.getLong("revision"),
            RagConfigStatus.valueOf(rs.getString("status")),
            readConfig(rs.getString("config_json")).withRevision(rs.getLong("revision")),
            rs.getString("description"), rs.getString("created_by"), instant(rs.getTimestamp("created_at")),
            rs.getString("validated_by"), instant(rs.getTimestamp("validated_at")),
            rs.getString("published_by"), instant(rs.getTimestamp("published_at")),
            rs.getObject("rollback_from_revision", Long.class), rs.getString("failure_reason"));

    RagConfigRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    void ensureSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS rag_config_revision ("
                + "revision BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "status VARCHAR(16) NOT NULL, config_json LONGTEXT NOT NULL, description VARCHAR(500),"
                + "created_by VARCHAR(128) NOT NULL, created_at DATETIME(3) NOT NULL,"
                + "validated_by VARCHAR(128), validated_at DATETIME(3),"
                + "published_by VARCHAR(128), published_at DATETIME(3),"
                + "rollback_from_revision BIGINT, failure_reason VARCHAR(1000),"
                + "INDEX idx_rag_config_status (status)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS rag_config_audit_log ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, revision BIGINT NOT NULL,"
                + "action VARCHAR(32) NOT NULL, operator VARCHAR(128) NOT NULL,"
                + "from_revision BIGINT, details_json LONGTEXT, created_at DATETIME(3) NOT NULL,"
                + "INDEX idx_rag_config_audit_revision (revision)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    Optional<RagConfigRevision> findActive() {
        return findOne("SELECT * FROM rag_config_revision WHERE status = 'ACTIVE' ORDER BY revision DESC LIMIT 1");
    }

    Optional<RagConfigRevision> findActiveForUpdate() {
        return findOne("SELECT * FROM rag_config_revision WHERE status = 'ACTIVE' ORDER BY revision DESC LIMIT 1 FOR UPDATE");
    }

    Optional<RagConfigRevision> findByRevision(long revision) {
        return findOne("SELECT * FROM rag_config_revision WHERE revision = ?", revision);
    }

    Optional<RagConfigRevision> findByRevisionForUpdate(long revision) {
        return findOne("SELECT * FROM rag_config_revision WHERE revision = ? FOR UPDATE", revision);
    }

    List<RagConfigRevision> findAll() {
        return jdbcTemplate.query("SELECT * FROM rag_config_revision ORDER BY revision DESC", rowMapper);
    }

    RagConfigRevision insert(RagConfigStatus status, RagRuntimeConfig config, String description, String operator,
                             Long rollbackFromRevision) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO rag_config_revision(status, config_json, description, created_by, created_at, rollback_from_revision)"
                            + " VALUES (?, ?, ?, ?, ?, ?)", new String[]{"revision"});
            statement.setString(1, status.name());
            statement.setString(2, writeConfig(config));
            statement.setString(3, description);
            statement.setString(4, operator);
            statement.setTimestamp(5, Timestamp.from(now));
            if (rollbackFromRevision == null) statement.setNull(6, java.sql.Types.BIGINT);
            else statement.setLong(6, rollbackFromRevision);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("无法生成配置版本号");
        return findByRevision(key.longValue()).orElseThrow(() -> new IllegalStateException("无法读取新建配置版本"));
    }

    void markValidated(long revision, String operator) {
        jdbcTemplate.update("UPDATE rag_config_revision SET status = 'VALIDATED', validated_by = ?, validated_at = ?"
                        + " WHERE revision = ?", operator, Timestamp.from(Instant.now()), revision);
    }

    void markActive(long revision, String operator, Long rollbackFromRevision) {
        jdbcTemplate.update("UPDATE rag_config_revision SET status = 'ACTIVE', published_by = ?, published_at = ?,"
                        + " rollback_from_revision = ?, failure_reason = NULL WHERE revision = ?",
                operator, Timestamp.from(Instant.now()), rollbackFromRevision, revision);
    }

    void markSuperseded(long revision) {
        jdbcTemplate.update("UPDATE rag_config_revision SET status = 'SUPERSEDED' WHERE revision = ?", revision);
    }

    void audit(long revision, String action, String operator, Long fromRevision, String detailsJson) {
        jdbcTemplate.update("INSERT INTO rag_config_audit_log(revision, action, operator, from_revision, details_json, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)", revision, action, operator, fromRevision,
                detailsJson, Timestamp.from(Instant.now()));
    }

    private Optional<RagConfigRevision> findOne(String sql, Object... arguments) {
        List<RagConfigRevision> rows = jdbcTemplate.query(sql, rowMapper, arguments);
        return rows.stream().findFirst();
    }

    private RagRuntimeConfig readConfig(String json) {
        try {
            return objectMapper.readValue(json, RagRuntimeConfig.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("RAG 配置版本数据无法解析", exception);
        }
    }

    private String writeConfig(RagRuntimeConfig config) {
        try {
            return objectMapper.writeValueAsString(config.withRevision(0));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("RAG 配置无法序列化", exception);
        }
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
