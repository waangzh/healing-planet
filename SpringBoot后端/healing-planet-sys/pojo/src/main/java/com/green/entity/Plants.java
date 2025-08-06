package com.green.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 植物信息库
 */
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
@TableName(value = "plants")
public class Plants {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 植物学名
     */
    @TableField(value = "scientific_name")
    private String scientificName;

    /**
     * 通用名
     */
    @TableField(value = "common_name")
    private String commonName;

    /**
     * 封面图
     */
    @TableField(value = "cover_img")
    private String coverImg;

    /**
     * 养护难度系数
     */
    @TableField(value = "difficulty")
    private Integer difficulty;

    /**
     * 创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;
}
