package com.example.demos.web.mapper;

import com.example.demos.web.pojo.entity.DetectInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DetectionMapper {

    /**
     * 插入识别记录
     *
     * @param detectInfo
     */
    @Insert("insert into detection_information (plant_name, detection_time, detection_result, device_id, " +
            "status, suggestion, detection_image) " +
            "VALUES (#{plantName},#{detectionTime},#{detectionResult},#{deviceId}," +
            "#{status},#{suggestion},#{detectionImageUrl})")
    void insertDetectInfo(DetectInfo detectInfo);
}
