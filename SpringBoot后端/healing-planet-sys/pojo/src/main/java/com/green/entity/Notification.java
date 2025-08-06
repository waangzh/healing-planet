package com.green.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
@TableName("notification")
public class Notification {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 触发用户id
     */
    @TableField(value = "sender_id")
    private String senderId;

    /**
     * 接收用户id
     */
    @TableField(value = "receiver_id")
    private String receiverId;

    /**
     * 消息类型 - 1点赞 2收藏 3关注 4评论 5回复
     */
    @TableField(value = "type")
    private Integer type;

    /**
     * 关联对象类型 - 1帖子 2评论 3用户
     */
    @TableField(value = "object_type")
    private Integer objectType;

    /**
     * 关联对象id
     */
    @TableField(value = "object_id")
    private String objectId;

    /**
     * 是否已读
     */
    @TableField(value = "is_read")
    @Builder.Default
    private Integer isRead;

    /**
     * 创建时间
     */
    @TableField(value = "created_at")
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 阅读时间
     */
    @TableField(value = "read_at")
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readAt;
}
