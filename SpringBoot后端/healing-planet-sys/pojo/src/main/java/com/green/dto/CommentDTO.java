package com.green.dto;

import lombok.Data;

import java.io.Serializable;


@Data
public class CommentDTO implements Serializable {
    private static final long serialVersionUID = -5957433707110390852L;

    private String userName;

    private String topic_id;

    /**
     * 内容
     */
    private String content;

    /**
     * 父评论id（必须）
     */
    private String parentId;

    /**
     * 被回复用户id（必须）
     */
    private String replyToUserId;
}
