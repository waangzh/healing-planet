package com.example.demos.web.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThresholdDto {
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
}
