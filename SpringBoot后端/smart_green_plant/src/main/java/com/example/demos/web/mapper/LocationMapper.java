package com.example.demos.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demos.web.pojo.entity.LocationData;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LocationMapper extends BaseMapper<LocationData> {

    /**
     * 根据设备id查询设备地址
     * @param deviceId
     * @return
     */
    @Select("select * from location_data where device_id=#{deviceId}")
    LocationData getLocationByDeviceId(Integer deviceId);

    /**
     * 更新设备地址信息
     * @param locationData
     */
    void updateLocation(LocationData locationData);

    /**
     * 插入新的设备地址信息
     * @param locationData
     */
    void insertLocation(LocationData locationData);

    /**
     * 删除设备地址信息
     * @param locationData
     */
    @Delete("delete from location_data where device_id=#{deviceId}")
    void deleteLocation(LocationData locationData);
}
