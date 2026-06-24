package com.example.demos.web.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("threshold")
public class Threshold {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer deviceId;
    private Float temperatureMin;
    private Float temperatureMax;
    private Float humidityMin;
    private Float humidityMax;
    private Float co2Min;
    private Float co2Max;
    private Float lightIntensityMin;
    private Float lightIntensityMax;
    private Float soilMoistureMin;
    private Float soilMoistureMax;
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;
}
