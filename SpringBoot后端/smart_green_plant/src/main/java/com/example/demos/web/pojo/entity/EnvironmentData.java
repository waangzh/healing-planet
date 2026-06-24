package com.example.demos.web.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@TableName("environment_data")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EnvironmentData {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer plantInstanceId;
    private Double temperature;
    private Double humidity;
    private Double lightIntensity;
    private Double soilMoisture;
    private Double co2Concentration;
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recordedTime;

    @Override
    public String toString() {
        return String.format(
                "时间: %s, 温度: %.2f°C, 环境湿度: %.2f%%, 土壤湿度: %.2f%%, 光照: %.2f Lux, CO2: %.2f ppm",
                recordedTime,
                temperature,
                humidity,
                soilMoisture,
                lightIntensity,
                co2Concentration
        );
    }
}
