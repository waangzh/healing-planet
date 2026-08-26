package com.green.outbox;

import com.green.mapper.PostIndexOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostIndexOutboxServiceTest {

    @Test
    void shouldCreatePendingUpsertEvent() {
        PostIndexOutboxMapper mapper = mock(PostIndexOutboxMapper.class);
        PostIndexOutboxService service = new PostIndexOutboxService(mapper);

        service.recordUpsert("post-42");

        ArgumentCaptor<PostIndexOutboxEvent> captor = ArgumentCaptor.forClass(PostIndexOutboxEvent.class);
        verify(mapper).insert(captor.capture());
        PostIndexOutboxEvent event = captor.getValue();
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getEventType()).isEqualTo(PostIndexEventType.POST_UPSERT.name());
        assertThat(event.getPostId()).isEqualTo("post-42");
        assertThat(event.getState()).isEqualTo(PostIndexOutboxState.PENDING);
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getNextAttemptAt()).isEqualTo(event.getOccurredAt());
    }
}
