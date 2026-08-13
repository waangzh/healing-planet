package com.example.demos.web.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlantStateVO {
    private Integer plantInstanceId;
    private Integer plantId;
    private String plantName;
    private Integer deviceId;
    private LocalDateTime observedAt;
    private SensorMetrics current;
    private SensorWindow last24h;
    private SensorWindow last7d;
    private SensorThresholds thresholds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SensorMetrics {
        private Double temperature;
        private Double humidity;
        private Double soilMoisture;
        private Double lightIntensity;
        private Double co2;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SensorWindow {
        private Integer sampleCount;
        private SensorMetrics average;
        private SensorMetrics minimum;
        private SensorMetrics maximum;
        private SensorTrends trends;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SensorTrends {
        private String temperature;
        private String humidity;
        private String soilMoisture;
        private String lightIntensity;
        private String co2;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SensorThresholds {
        private Double temperatureMin;
        private Double temperatureMax;
        private Double humidityMin;
        private Double humidityMax;
        private Double soilMoistureMin;
        private Double soilMoistureMax;
        private Double lightIntensityMin;
        private Double lightIntensityMax;
        private Double co2Min;
        private Double co2Max;
    }
}
