package com.green.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("private_message")
public class PrivateMessage {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    @TableField("from_user_id")
    private String fromUserId;

    @TableField("to_user_id")
    private String toUserId;

    @TableField("content")
    private String content;

    @Builder.Default
    @TableField("is_read")
    private Integer isRead = 0;

    @TableField("created_at")
    @JsonFormat(locale = "zh", timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @TableField("read_at")
    @JsonFormat(locale = "zh", timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readAt;
}
