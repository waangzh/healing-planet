package com.green.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Date;


@Data
@Builder
@TableName("comment")
@AllArgsConstructor
@NoArgsConstructor
public class Comment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 内容
     */
    @NotBlank(message = "内容不可以为空")
    @TableField(value = "content")
    private String content;


    /**
     * 作者ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * topicID
     */
    @TableField("topic_id")
    private String topicId;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private Date createTime;


    /**
     * 父级评论id
     */
    @TableField("parent_id")
    private String parentId;

    /**
     * 根评论id
     */
    @TableField("root_id")
    private String rootId;

    /**
     * 回复用户的id（实现@功能）
     */
    @TableField("reply_to_user_id")
    private String replyToUserId;

    /**
     * 评论层级
     */
    @TableField("level")
    private Integer level;
}
