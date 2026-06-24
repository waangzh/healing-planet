package com.example.demos.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demos.web.pojo.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface DeviceMapper extends BaseMapper<Device> {

    /**
     * 根据植物实例id称查询设备
     * @param plantInstanceId
     * @return
     */
    @Select("SELECT name FROM device WHERE plant_instance_id = #{plantInstanceId}")
    String getDeviceName(Integer plantInstanceId);

    /**
     * 根据设备名称查询植物实例id
     * @param name
     * @return
     */
    @Select("select plant_instance_id from device where name = #{name}")
    Integer getPlantInstanceIdByName(String name);
}
