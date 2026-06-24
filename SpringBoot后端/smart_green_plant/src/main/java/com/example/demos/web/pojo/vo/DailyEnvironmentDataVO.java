package com.example.demos.web.pojo.vo;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailyEnvironmentDataVO {

    private Double temperature;
    private Double humidity;
    private Double lightIntensity;
    private Double soilMoisture;
    private Double co2Concentration;
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="MM-dd HH:mm:ss")
    private LocalDateTime recordedTime;
}
