package com.example.demos.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demos.web.pojo.entity.PlantInstance;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface PlantInstanceMapper extends BaseMapper<PlantInstance> {

    @Select("SELECT * FROM plant_instance WHERE user_id = #{userId}")
    List<PlantInstance> list(Integer userId);

    @Select("SELECT plant.name from plant join plant_instance on " +
            "plant.id = plant_instance.plant_id where plant_instance.device_id = #{deviceId}")
    List<String> getPlantNameByDeviceId(Integer deviceId);

    @Update("update plant_instance set healthy_condition = #{diseaseName} ")
    void updateHealthyCondition(String diseaseName);

    /**
     * 在社区发送请求到后台，更新中间表中的绑定关系
     * @param plantInstance
     */

    @Update("update plant_instance set user_id = #{userId} where device_id = #{deviceId}")
    void updateData(PlantInstance plantInstance);

    /**
     * 在添加设备的时候，往这张中间表中添加记录并和用户绑定起来
     * @param plantInstance
     */
    @Insert("insert into plant_instance (device_id) values (#{deviceId})")
    void insertData(PlantInstance plantInstance);
}
