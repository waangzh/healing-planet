// config/RabbitConfig.java
package com.green.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "notification.exchange";
    public static final String QUEUE = "notification.queue";
    public static final String ROUTING_KEY = "notification.key";
    public static final String POST_INDEX_EXCHANGE = "rag.post-index.exchange";
    public static final String POST_INDEX_QUEUE = "rag.post-index.queue";
    public static final String POST_INDEX_ROUTING_KEY = "rag.post-index";
    public static final String POST_INDEX_DEAD_LETTER_EXCHANGE = "rag.post-index.dlx";
    public static final String POST_INDEX_DEAD_LETTER_QUEUE = "rag.post-index.dlq";
    public static final String POST_INDEX_DEAD_LETTER_ROUTING_KEY = "rag.post-index.dead";

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(notificationExchange()).with(ROUTING_KEY);
    }

    @Bean
    public DirectExchange postIndexExchange() {
        return new DirectExchange(POST_INDEX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange postIndexDeadLetterExchange() {
        return new DirectExchange(POST_INDEX_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue postIndexQueue() {
        return QueueBuilder.durable(POST_INDEX_QUEUE)
                .withArgument("x-dead-letter-exchange", POST_INDEX_DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", POST_INDEX_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue postIndexDeadLetterQueue() {
        return QueueBuilder.durable(POST_INDEX_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding postIndexBinding() {
        return BindingBuilder.bind(postIndexQueue()).to(postIndexExchange()).with(POST_INDEX_ROUTING_KEY);
    }

    @Bean
    public Binding postIndexDeadLetterBinding() {
        return BindingBuilder.bind(postIndexDeadLetterQueue()).to(postIndexDeadLetterExchange())
                .with(POST_INDEX_DEAD_LETTER_ROUTING_KEY);
    }
}
