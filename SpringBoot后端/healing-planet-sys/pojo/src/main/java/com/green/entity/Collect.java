package com.green.entity;


import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@TableName("collect")
@AllArgsConstructor
@NoArgsConstructor
public class Collect implements Serializable {

    /**
     * 主键
     */
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
