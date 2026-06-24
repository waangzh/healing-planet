package com.example.demos.web.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@TableName("plant_instance")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlantInstance {
    //植物实例id
    @TableId(type=IdType.AUTO)
    private Integer id;
    //用户id
    private Integer userId;
    //用户种植的植物id
    private Integer plantId;
    //设备id
    private Integer deviceId;
    //用户种植绿植的定位
    private Integer locationId;
    @TableField("healthy_condition")
    private String healthyCondition;
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    @TableField("date_planted")
    private LocalDateTime datePlanted;

    /**
     * 植物照片
     */
    @TableField("img_url")
    private String imgUrl;
}
