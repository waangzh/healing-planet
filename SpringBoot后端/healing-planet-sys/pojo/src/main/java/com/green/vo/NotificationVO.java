package com.green.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class NotificationVO {

    /**
     * 消息id
     */
    private String id;

    /**
     * 触发用户id
     */
    private String senderId;

    /**
     * 触发用户用户名
     */
    private String senderName;

    /**
     * 触发用户头像
     */
    private String senderAvatar;


    /**
     * 消息类型 - 1点赞 2收藏 3关注 4评论 5回复
     */
    private String type;

    /**
     * 关联对象类型 - 1帖子 2评论 3用户
     */
    private String objectType;

    /**
     * 关联对象id
     */
    private String objectId;

    /**
     * 是否已读
     */
    private Boolean isRead;

    /**
     * 创建时间
     */
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;


}
