package com.green.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.config.PostIndexOutboxProperties;
import com.green.config.RabbitConfig;
import com.green.mapper.PostIndexOutboxMapper;
import com.green.service.RagPostIndexClient;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Date;

@Component
@Slf4j
public class PostIndexOutboxConsumer {
    private final ObjectMapper objectMapper;
    private final PostIndexOutboxMapper outboxMapper;
    private final RagPostIndexClient ragPostIndexClient;
    private final PostIndexOutboxProperties properties;

    public PostIndexOutboxConsumer(ObjectMapper objectMapper, PostIndexOutboxMapper outboxMapper,
                                   RagPostIndexClient ragPostIndexClient, PostIndexOutboxProperties properties) {
        this.objectMapper = objectMapper;
        this.outboxMapper = outboxMapper;
        this.ragPostIndexClient = ragPostIndexClient;
        this.properties = properties;
    }

    @RabbitListener(queues = RabbitConfig.POST_INDEX_QUEUE,
            containerFactory = "postIndexOutboxListenerContainerFactory")
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String eventId = eventIdFrom(message);
        try {
            PostIndexEventMessage event = objectMapper.readValue(message.getBody(), PostIndexEventMessage.class);
            validate(event, eventId);
            eventId = event.getEventId();

            PostIndexOutboxEvent outboxEvent = outboxMapper.selectById(eventId);
            if (outboxEvent == null || PostIndexOutboxState.DELIVERED.equals(outboxEvent.getState())
                    || PostIndexOutboxState.PENDING.equals(outboxEvent.getState())) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            ragPostIndexClient.apply(event);
            outboxMapper.markDelivered(eventId);
            channel.basicAck(deliveryTag, false);
        } catch (IllegalArgumentException exception) {
            markFailed(eventId, "无效帖子索引消息: " + PostIndexOutboxPublisher.safeMessage(exception));
            channel.basicReject(deliveryTag, false);
        } catch (Exception exception) {
            try {
                if (StringUtils.hasText(eventId)) {
                    PostIndexOutboxEvent event = outboxMapper.selectById(eventId);
                    if (event != null && !PostIndexOutboxState.DELIVERED.equals(event.getState())) {
                        outboxMapper.reschedule(eventId, retryAt(event), PostIndexOutboxPublisher.truncate(
                                "调用 RAG 索引接口失败: " + PostIndexOutboxPublisher.safeMessage(exception)));
                    }
                    channel.basicAck(deliveryTag, false);
                    return;
                }
            } catch (Exception persistenceException) {
                log.error("记录帖子索引消费失败状态时出错，消息将重新入队", persistenceException);
            }
            log.error("消费帖子索引消息失败，消息将重新入队", exception);
            channel.basicReject(deliveryTag, false);
        }
    }

    private void validate(PostIndexEventMessage event, String headerEventId) {
        if (event == null || !StringUtils.hasText(event.getEventId()) || !StringUtils.hasText(event.getPostId())
                || !StringUtils.hasText(event.getType()) || !StringUtils.hasText(event.getOccurredAt())) {
            throw new IllegalArgumentException("缺少 eventId、type、postId 或 occurredAt");
        }
        if (StringUtils.hasText(headerEventId) && !headerEventId.equals(event.getEventId())) {
            throw new IllegalArgumentException("消息头 eventId 与载荷不一致");
        }
        PostIndexEventType.valueOf(event.getType());
    }

    private String eventIdFrom(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(PostIndexOutboxPublisher.EVENT_ID_HEADER);
        return value == null ? null : String.valueOf(value);
    }

    private void markFailed(String eventId, String error) {
        if (StringUtils.hasText(eventId)) {
            outboxMapper.markFailed(eventId, PostIndexOutboxPublisher.truncate(error));
        }
    }

    private Date retryAt(PostIndexOutboxEvent event) {
        long delay = properties.getInitialRetryDelayMillis();
        int attempts = event.getAttemptCount() == null ? 1 : Math.max(1, event.getAttemptCount());
        for (int i = 1; i < attempts; i++) {
            delay = Math.min(properties.getMaxRetryDelayMillis(), delay * 2);
        }
        return new Date(System.currentTimeMillis() + delay);
    }
}
