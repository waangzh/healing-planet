package com.green.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 植物养护详细指南
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "plant_care_guides")
public class PlantCareGuides {

    /**
     * 主键
     */
    @TableId(value = "id",type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 植物主键
     */
    @TableField(value = "plant_id")
    private String plantId;

    /**
     * 光照需求
     */
    @TableField(value = "light_requirements")
    private String lightRequirements;


    /**
     * 浇水频率
     */
    @TableField(value = "watering_frequency")
    private String wateringFrequency;


    /**
     * 温度偏好
     */
    @TableField(value = "temperature_preference")
    private String temperaturePreference;


    /**
     * 湿度偏好
     */
    @TableField(value = "humidity_preference")
    private String humidityPreference;

    /**
     * 施肥技巧
     */
    @TableField(value = "fertilizing_tips")
    private String fertilizingTips;


    /**
     * 细节建议
     */
    @TableField(value = "detail_advice")
    private String detailAdvice;
}
