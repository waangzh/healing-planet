package com.green.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.config.PostIndexOutboxProperties;
import com.green.config.RabbitConfig;
import com.green.mapper.PostIndexOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class PostIndexOutboxPublisher {
    public static final String EVENT_ID_HEADER = "x-post-index-event-id";

    private final PostIndexOutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final PostIndexOutboxProperties properties;

    public PostIndexOutboxPublisher(PostIndexOutboxMapper outboxMapper, RabbitTemplate rabbitTemplate,
                                    ObjectMapper objectMapper, PostIndexOutboxProperties properties) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    void configureCallbacks() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlation, ack, cause) -> {
            if (correlation == null || correlation.getId() == null) {
                return;
            }
            if (ack) {
                outboxMapper.markPublished(correlation.getId());
            } else {
                reschedule(correlation.getId(), "RabbitMQ publisher confirm 未确认: "
                        + (cause == null ? "unknown" : cause), 1);
            }
        });
        rabbitTemplate.setReturnsCallback(this::handleReturnedMessage);
    }

    @Scheduled(fixedDelayString = "${app.post-index-outbox.publish-fixed-delay-millis:5000}")
    public void publishPendingEvents() {
        if (!properties.isEnabled()) {
            return;
        }
        validateProperties();
        List<PostIndexOutboxEvent> events = outboxMapper.selectPublishable(properties.getBatchSize());
        for (PostIndexOutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(PostIndexOutboxEvent event) {
        Date lockedUntil = new Date(System.currentTimeMillis() + properties.getLeaseMillis());
        if (outboxMapper.claimForPublishing(event.getEventId(), lockedUntil) != 1) {
            return;
        }
        try {
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            messageProperties.setMessageId(event.getEventId());
            messageProperties.setHeader(EVENT_ID_HEADER, event.getEventId());
            Message message = new Message(serialize(event).getBytes(StandardCharsets.UTF_8), messageProperties);
            rabbitTemplate.send(RabbitConfig.POST_INDEX_EXCHANGE, RabbitConfig.POST_INDEX_ROUTING_KEY, message,
                    new CorrelationData(event.getEventId()));
        } catch (RuntimeException exception) {
            reschedule(event.getEventId(), "发送 RabbitMQ 失败: " + safeMessage(exception), attemptsAfterClaim(event));
        }
    }

    private void handleReturnedMessage(ReturnedMessage returned) {
        Object value = returned.getMessage().getMessageProperties().getHeaders().get(EVENT_ID_HEADER);
        if (value == null) {
            log.error("RAG 索引消息无法路由且缺少 eventId，replyCode={}, replyText={}",
                    returned.getReplyCode(), returned.getReplyText());
            return;
        }
        reschedule(String.valueOf(value), "RabbitMQ 无可用路由: " + returned.getReplyText(), 1);
    }

    private String serialize(PostIndexOutboxEvent event) {
        try {
            String occurredAt = DateTimeFormatter.ISO_INSTANT.format(event.getOccurredAt().toInstant());
            return objectMapper.writeValueAsString(new PostIndexEventMessage(event.getEventId(), event.getEventType(),
                    event.getPostId(), occurredAt));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化帖子索引 outbox 事件", exception);
        }
    }

    private void reschedule(String eventId, String error, int attemptCount) {
        outboxMapper.reschedule(eventId, retryAt(attemptCount), truncate(error));
    }

    private Date retryAt(int attemptCount) {
        long delay = properties.getInitialRetryDelayMillis();
        for (int i = 1; i < Math.max(1, attemptCount); i++) {
            delay = Math.min(properties.getMaxRetryDelayMillis(), delay * 2);
        }
        return new Date(System.currentTimeMillis() + delay);
    }

    private int attemptsAfterClaim(PostIndexOutboxEvent event) {
        return (event.getAttemptCount() == null ? 0 : event.getAttemptCount()) + 1;
    }

    private void validateProperties() {
        if (properties.getBatchSize() <= 0 || properties.getLeaseMillis() <= 0
                || properties.getInitialRetryDelayMillis() <= 0 || properties.getMaxRetryDelayMillis() <= 0) {
            throw new IllegalStateException("app.post-index-outbox 的批次、租约和重试时间必须为正数");
        }
    }

    static String truncate(String error) {
        return error == null ? "unknown" : error.substring(0, Math.min(error.length(), 1000));
    }

    static String safeMessage(Throwable exception) {
        return exception == null || exception.getMessage() == null
                ? exception == null ? "unknown" : exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
