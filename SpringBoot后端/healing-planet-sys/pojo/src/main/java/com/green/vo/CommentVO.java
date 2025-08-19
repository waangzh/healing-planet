package com.green.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;


@Data
public class CommentVO {

    private String id;

    /**
     * 根评论id
     */
    private String rootId;

    private String content;

    /**
     * 文章名
     */
    private String topic;

    private String topicId;

    /**
     * 创建评论的用户id
     */
    private String userId;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 创建评论的用户名
     */
    private String authorName;

    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String replyToUserId;//回复用户id

    private String replyToUsername;//被回复用户名

    private List<CommentVO> children; // 子评论列表
}
