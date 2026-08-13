package com.example.demos.web.pojo.vo;

import lombok.Data;

@Data
public class SensorWindowStatistics {
    private Integer sampleCount;
    private Double avgTemperature;
    private Double minTemperature;
    private Double maxTemperature;
    private Double avgHumidity;
    private Double minHumidity;
    private Double maxHumidity;
    private Double avgSoilMoisture;
    private Double minSoilMoisture;
    private Double maxSoilMoisture;
    private Double avgLightIntensity;
    private Double minLightIntensity;
    private Double maxLightIntensity;
    private Double avgCo2;
    private Double minCo2;
    private Double maxCo2;
}
