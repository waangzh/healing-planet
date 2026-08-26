package com.green.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("post_index_outbox")
public class PostIndexOutboxEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "event_id", type = IdType.INPUT)
    private String eventId;
    @TableField("event_type")
    private String eventType;
    @TableField("post_id")
    private String postId;
    @TableField("occurred_at")
    private Date occurredAt;
    private String state;
    @TableField("attempt_count")
    private Integer attemptCount;
    @TableField("next_attempt_at")
    private Date nextAttemptAt;
    @TableField("locked_until")
    private Date lockedUntil;
    @TableField("published_at")
    private Date publishedAt;
    @TableField("delivered_at")
    private Date deliveredAt;
    @TableField("last_error")
    private String lastError;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
}
