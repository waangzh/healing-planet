package com.green.vo;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlantsVO implements Serializable {
    /**
     * 主键
     */
    private String id;

    /**
     * 植物学名
     */
    private String scientificName;

    /**
     * 通用名
     */
    private String commonName;

    /**
     * 封面图
     */
    private String coverImg;

    /**
     * 养护难度系数
     */
    private Integer difficulty;

    /**
     * 创建时间
     */
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;



    /**
     * 光照需求
     */
    private String lightRequirements;


    /**
     * 浇水频率
     */
    private String wateringFrequency;


    /**
     * 温度偏好
     */
    private String temperaturePreference;


    /**
     * 湿度偏好
     */
    private String humidityPreference;

    /**
     * 施肥技巧
     */
    private String fertilizingTips;


    /**
     * 细节建议
     */
    private String detailAdvice;

}
