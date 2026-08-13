package com.example.demos.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demos.web.mapper.EnvironmentDataMapper;
import com.example.demos.web.mapper.PlantInstanceMapper;
import com.example.demos.web.mapper.PlantMapper;
import com.example.demos.web.mapper.ThresholdMapper;
import com.example.demos.web.pojo.entity.EnvironmentData;
import com.example.demos.web.pojo.entity.Plant;
import com.example.demos.web.pojo.entity.PlantInstance;
import com.example.demos.web.pojo.entity.Threshold;
import com.example.demos.web.pojo.vo.PlantStateVO;
import com.example.demos.web.pojo.vo.SensorWindowStatistics;
import com.example.demos.web.service.PlantStateService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PlantStateServiceImpl implements PlantStateService {
    private final PlantInstanceMapper plantInstanceMapper;
    private final PlantMapper plantMapper;
    private final EnvironmentDataMapper environmentDataMapper;
    private final ThresholdMapper thresholdMapper;

    public PlantStateServiceImpl(PlantInstanceMapper plantInstanceMapper, PlantMapper plantMapper,
                                 EnvironmentDataMapper environmentDataMapper, ThresholdMapper thresholdMapper) {
        this.plantInstanceMapper = plantInstanceMapper;
        this.plantMapper = plantMapper;
        this.environmentDataMapper = environmentDataMapper;
        this.thresholdMapper = thresholdMapper;
    }

    @Override
    public Optional<PlantStateVO> getState(Integer plantInstanceId, Long userId) {
        PlantInstance instance = plantInstanceMapper.selectById(plantInstanceId);
        if (instance == null || instance.getUserId() == null || userId == null
                || instance.getUserId().longValue() != userId.longValue()) {
            return Optional.empty();
        }
        EnvironmentData latest = environmentDataMapper.findLatest(plantInstanceId);
        if (latest == null) return Optional.empty();

        LocalDateTime now = LocalDateTime.now();
        Plant plant = instance.getPlantId() == null ? null : plantMapper.selectById(instance.getPlantId());
        Threshold threshold = findThreshold(instance.getDeviceId());
        return Optional.of(PlantStateVO.builder()
                .plantInstanceId(instance.getId())
                .plantId(instance.getPlantId())
                .plantName(plant == null ? null : plant.getName())
                .deviceId(instance.getDeviceId())
                .observedAt(latest.getRecordedTime())
                .current(metrics(latest))
                .last24h(window(plantInstanceId, now.minusHours(24), latest))
                .last7d(window(plantInstanceId, now.minusDays(7), latest))
                .thresholds(thresholds(threshold))
                .build());
    }

    private Threshold findThreshold(Integer deviceId) {
        if (deviceId == null) return null;
        return thresholdMapper.selectOne(new QueryWrapper<Threshold>()
                .eq("device_id", deviceId).orderByDesc("updated_time").last("LIMIT 1"));
    }

    private PlantStateVO.SensorWindow window(Integer plantInstanceId, LocalDateTime since, EnvironmentData latest) {
        SensorWindowStatistics stats = environmentDataMapper.summarizeSince(plantInstanceId, since);
        EnvironmentData first = environmentDataMapper.findFirstSince(plantInstanceId, since);
        if (stats == null || stats.getSampleCount() == null || stats.getSampleCount() == 0) {
            return PlantStateVO.SensorWindow.builder().sampleCount(0).build();
        }
        return PlantStateVO.SensorWindow.builder()
                .sampleCount(stats.getSampleCount())
                .average(PlantStateVO.SensorMetrics.builder().temperature(stats.getAvgTemperature())
                        .humidity(stats.getAvgHumidity()).soilMoisture(stats.getAvgSoilMoisture())
                        .lightIntensity(stats.getAvgLightIntensity()).co2(stats.getAvgCo2()).build())
                .minimum(PlantStateVO.SensorMetrics.builder().temperature(stats.getMinTemperature())
                        .humidity(stats.getMinHumidity()).soilMoisture(stats.getMinSoilMoisture())
                        .lightIntensity(stats.getMinLightIntensity()).co2(stats.getMinCo2()).build())
                .maximum(PlantStateVO.SensorMetrics.builder().temperature(stats.getMaxTemperature())
                        .humidity(stats.getMaxHumidity()).soilMoisture(stats.getMaxSoilMoisture())
                        .lightIntensity(stats.getMaxLightIntensity()).co2(stats.getMaxCo2()).build())
                .trends(trends(first, latest)).build();
    }

    private PlantStateVO.SensorMetrics metrics(EnvironmentData data) {
        return PlantStateVO.SensorMetrics.builder().temperature(data.getTemperature()).humidity(data.getHumidity())
                .soilMoisture(data.getSoilMoisture()).lightIntensity(data.getLightIntensity())
                .co2(data.getCo2Concentration()).build();
    }

    private PlantStateVO.SensorTrends trends(EnvironmentData first, EnvironmentData latest) {
        if (first == null) return new PlantStateVO.SensorTrends();
        return PlantStateVO.SensorTrends.builder()
                .temperature(trend(first.getTemperature(), latest.getTemperature()))
                .humidity(trend(first.getHumidity(), latest.getHumidity()))
                .soilMoisture(trend(first.getSoilMoisture(), latest.getSoilMoisture()))
                .lightIntensity(trend(first.getLightIntensity(), latest.getLightIntensity()))
                .co2(trend(first.getCo2Concentration(), latest.getCo2Concentration())).build();
    }

    static String trend(Double first, Double latest) {
        if (first == null || latest == null) return "UNKNOWN";
        double tolerance = Math.max(Math.abs(first) * 0.05d, 0.1d);
        if (latest - first > tolerance) return "INCREASING";
        if (first - latest > tolerance) return "DECREASING";
        return "STABLE";
    }

    private PlantStateVO.SensorThresholds thresholds(Threshold value) {
        if (value == null) return null;
        return PlantStateVO.SensorThresholds.builder()
                .temperatureMin(asDouble(value.getTemperatureMin())).temperatureMax(asDouble(value.getTemperatureMax()))
                .humidityMin(asDouble(value.getHumidityMin())).humidityMax(asDouble(value.getHumidityMax()))
                .soilMoistureMin(asDouble(value.getSoilMoistureMin())).soilMoistureMax(asDouble(value.getSoilMoistureMax()))
                .lightIntensityMin(asDouble(value.getLightIntensityMin())).lightIntensityMax(asDouble(value.getLightIntensityMax()))
                .co2Min(asDouble(value.getCo2Min())).co2Max(asDouble(value.getCo2Max())).build();
    }

    private Double asDouble(Float value) { return value == null ? null : value.doubleValue(); }
}
