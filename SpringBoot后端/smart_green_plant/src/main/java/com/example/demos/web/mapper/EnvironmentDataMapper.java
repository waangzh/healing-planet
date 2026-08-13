package com.example.demos.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demos.web.pojo.entity.*;
import com.example.demos.web.pojo.vo.SensorWindowStatistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
@Repository
public interface EnvironmentDataMapper extends BaseMapper<EnvironmentData> {


    @Select("SELECT temperature,recorded_time FROM environment_data WHERE plant_instance_id = #{plantInstanceId}")
    List<Temperature> getTemperature(Integer plantInstanceId);

    @Select("SELECT humidity,recorded_time FROM environment_data WHERE plant_instance_id = #{plantInstanceId}")
    List<EnvironmentHumidity> getHumidity(Integer plantInstanceId);

    @Select("SELECT light_intensity,recorded_time FROM environment_data WHERE plant_instance_id = #{plantInstanceId}")
    List<LightLux> getLightIntensity(Integer plantInstanceId);

    @Select("SELECT co2_concentration,recorded_time FROM environment_data WHERE plant_instance_id = #{plantInstanceId}")
    List<CO2Value> getCO2Concentration(Integer plantInstanceId);

    @Select("SELECT soil_moisture,recorded_time FROM environment_data WHERE plant_instance_id = #{plantInstanceId}")
    List<SoilMoisture> getSoilMoisture(Integer plantInstanceId);

    @Select("SELECT " +
            "DATE_FORMAT(recorded_time, '%Y-%m-%d') as recorded_day, " +
            "AVG(temperature) as temperature, " +
            "AVG(humidity) as humidity, " +
            "AVG(light_intensity) as lightIntensity, " +
            "AVG(soil_moisture) as soilMoisture, " +
            "AVG(co2_concentration) as co2Concentration " +
            "FROM environment_data " +
            "WHERE plant_instance_id = #{plantInstanceId} AND recorded_time >= #{startDate} " +
            "GROUP BY recorded_day " +
            "ORDER BY recorded_day ASC")
    List<Map<String, Object>> getDailyAverageData(@Param("plantInstanceId") String plantInstanceId, @Param("startDate") LocalDateTime startDate);

    @Select("SELECT * FROM environment_data WHERE plant_instance_id = #{plantInstanceId} " +
            "ORDER BY recorded_time DESC LIMIT 1")
    EnvironmentData findLatest(@Param("plantInstanceId") Integer plantInstanceId);

    @Select("SELECT * FROM environment_data WHERE plant_instance_id = #{plantInstanceId} " +
            "AND recorded_time >= #{since} ORDER BY recorded_time ASC LIMIT 1")
    EnvironmentData findFirstSince(@Param("plantInstanceId") Integer plantInstanceId,
                                   @Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) AS sample_count, " +
            "AVG(temperature) AS avg_temperature, MIN(temperature) AS min_temperature, MAX(temperature) AS max_temperature, " +
            "AVG(humidity) AS avg_humidity, MIN(humidity) AS min_humidity, MAX(humidity) AS max_humidity, " +
            "AVG(soil_moisture) AS avg_soil_moisture, MIN(soil_moisture) AS min_soil_moisture, MAX(soil_moisture) AS max_soil_moisture, " +
            "AVG(light_intensity) AS avg_light_intensity, MIN(light_intensity) AS min_light_intensity, MAX(light_intensity) AS max_light_intensity, " +
            "AVG(co2_concentration) AS avg_co2, MIN(co2_concentration) AS min_co2, MAX(co2_concentration) AS max_co2 " +
            "FROM environment_data WHERE plant_instance_id = #{plantInstanceId} AND recorded_time >= #{since}")
    SensorWindowStatistics summarizeSince(@Param("plantInstanceId") Integer plantInstanceId,
                                          @Param("since") LocalDateTime since);

}
