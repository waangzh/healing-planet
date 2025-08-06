package com.green.vo;

import lombok.Data;

@Data
public class PostLikesVO {

    /**
     * 是否点赞
     */
    private Boolean isLiked;

    /**
     * 话题id
     */
    private String topicId;

    /**
     * 点赞总数
     */
    private Integer likes;
}
