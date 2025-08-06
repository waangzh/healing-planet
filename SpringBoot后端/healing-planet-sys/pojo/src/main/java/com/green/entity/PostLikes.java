package com.green.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章点赞关系
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("post_likes")
public class PostLikes {

    /**
     * 主键
     */
    @TableId("id")
    private String id;

    /**
     * 文章id
     */
    private String topicId;

    /**
     * 用户id
     */
    private String userId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
