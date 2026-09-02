package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceFreshnessRepositoryTest {

    @Test
    void communityFreshnessShouldUseUndeliveredOutboxBacklogInsteadOfPostModifyTime() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class)))
                .thenReturn(SourceFreshnessRepository.SourceLag.unsupported());
        SourceFreshnessRepository repository = new SourceFreshnessRepository(jdbcTemplate);

        repository.findLag(KnowledgeSource.COMMUNITY, "fingerprint");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("post_index_outbox", "delivered_at is null", "POST_DELETE")
                .doesNotContain("modify_time", "indexed_at", " from post ");
    }

    @Test
    void diseaseFreshnessShouldComparePersistedSourceVersionRatherThanIndexWriteTime() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(SourceFreshnessRepository.SourceLag.unsupported());
        SourceFreshnessRepository repository = new SourceFreshnessRepository(jdbcTemplate);

        repository.findLag(KnowledgeSource.DISEASE, "fingerprint");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains("min(s.source_updated_at)", "s.index_fingerprint = ?")
                .doesNotContain("s.indexed_at");
    }
}
