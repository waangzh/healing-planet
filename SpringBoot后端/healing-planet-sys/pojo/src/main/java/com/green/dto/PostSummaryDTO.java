package com.green.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PostSummaryDTO {


    /**
     * 主键
     */
    private String postId;

    /**
     * 标题
     */
    private String title;

    /**
     * 评论总数
     */
    @Builder.Default
    private Integer totalComments = 0;

    /**
     * 收藏总数
     */
    @Builder.Default
    private Integer totalCollects = 0;

    /**
     * 浏览总数
     */
    @Builder.Default
    private Integer totalViews = 0;

    /**
     * 喜欢总数
     */
    @Builder.Default
    private Integer totalLikes = 0;

    private Long userViewTotal = 0L;

    /**
     * 用户观看时间
     */
    private Date viewTime;

}
