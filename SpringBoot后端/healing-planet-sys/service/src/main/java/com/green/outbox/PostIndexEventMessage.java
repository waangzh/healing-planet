package com.green.outbox;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** RabbitMQ 载荷；字段名与 AI 服务 README 中声明的跨服务契约一致。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostIndexEventMessage {
    private String eventId;
    private String type;
    private String postId;
    private String occurredAt;
}
