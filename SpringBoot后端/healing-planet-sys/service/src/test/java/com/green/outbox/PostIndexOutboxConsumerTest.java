package com.green.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.config.PostIndexOutboxProperties;
import com.green.mapper.PostIndexOutboxMapper;
import com.green.service.RagPostIndexClient;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class PostIndexOutboxConsumerTest {

    @Test
    void shouldMarkDeliveredOnlyAfterIndexCallSucceeds() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.mapper.selectById("event-1")).thenReturn(publishedEvent("event-1"));

        fixture.consumer.consume(message("event-1"), fixture.channel);

        verify(fixture.client).apply(any(PostIndexEventMessage.class));
        verify(fixture.mapper).markDelivered("event-1");
        verify(fixture.channel).basicAck(7L, false);
        verify(fixture.mapper, never()).reschedule(any(String.class), any(Date.class), any(String.class));
    }

    @Test
    void shouldRescheduleAndAcknowledgeWhenIndexCallFails() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.mapper.selectById("event-1")).thenReturn(publishedEvent("event-1"));
        doThrow(new IllegalStateException("AI unavailable")).when(fixture.client).apply(any(PostIndexEventMessage.class));

        fixture.consumer.consume(message("event-1"), fixture.channel);

        verify(fixture.mapper).reschedule(eq("event-1"), any(Date.class), contains("AI unavailable"));
        verify(fixture.channel).basicAck(7L, false);
        verify(fixture.mapper, never()).markDelivered("event-1");
    }

    private static PostIndexOutboxEvent publishedEvent(String eventId) {
        return PostIndexOutboxEvent.builder()
                .eventId(eventId)
                .state(PostIndexOutboxState.PUBLISHED)
                .attemptCount(1)
                .build();
    }

    private static Message message(String eventId) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7L);
        properties.setHeader(PostIndexOutboxPublisher.EVENT_ID_HEADER, eventId);
        PostIndexEventMessage event = new PostIndexEventMessage(eventId, PostIndexEventType.POST_UPSERT.name(),
                "post-42", "2026-08-26T00:00:00Z");
        return new Message(new ObjectMapper().writeValueAsBytes(event), properties);
    }

    private static class Fixture {
        private final PostIndexOutboxMapper mapper = mock(PostIndexOutboxMapper.class);
        private final RagPostIndexClient client = mock(RagPostIndexClient.class);
        private final Channel channel = mock(Channel.class);
        private final PostIndexOutboxConsumer consumer = new PostIndexOutboxConsumer(new ObjectMapper(), mapper, client,
                new PostIndexOutboxProperties());
    }
}
