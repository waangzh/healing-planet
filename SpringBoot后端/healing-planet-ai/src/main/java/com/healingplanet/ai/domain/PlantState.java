package com.healingplanet.ai.domain;

import java.time.LocalDateTime;

public record PlantState(
        Long plantInstanceId,
        String plantId,
        String plantName,
        Long deviceId,
        LocalDateTime observedAt,
        SensorMetrics current,
        SensorWindow last24h,
        SensorWindow last7d,
        SensorThresholds thresholds
) {
    public record SensorMetrics(Double temperature, Double humidity, Double soilMoisture,
                                Double lightIntensity, Double co2) { }

    public record SensorWindow(Integer sampleCount, SensorMetrics average, SensorMetrics minimum,
                               SensorMetrics maximum, SensorTrends trends) { }

    public record SensorTrends(String temperature, String humidity, String soilMoisture,
                               String lightIntensity, String co2) { }

    public record SensorThresholds(Double temperatureMin, Double temperatureMax,
                                   Double humidityMin, Double humidityMax,
                                   Double soilMoistureMin, Double soilMoistureMax,
                                   Double lightIntensityMin, Double lightIntensityMax,
                                   Double co2Min, Double co2Max) { }
}
