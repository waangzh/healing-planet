package com.example.demos.web.mapper;


import com.example.demos.web.pojo.dto.EnvironmentDataDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.xmlbeans.impl.xb.xmlconfig.Extensionconfig;

import java.util.List;


@Mapper
public interface DataSharedMapper {

    @Select("select distinct plant.name from binding_records bi join plant_instance pi " +
            "on pi.user_id = (select distinct back_end_user_id from binding_records where community_user_id = #{communityUserId}) " +
            "join plant on pi.plant_id = plant.id")
    List<String> getPlantName(String communityUserId);

    /**
     * 通过社区用户id查询，后台用户购买了多少台设备
     * @param communityUserId
     * @return
     */
    @Select("select device_id from binding_records where community_user_id =#{communityUserId}")
    List<Integer> getDeviceById(String communityUserId);

    /**
     * 获取所有环境数据及其植物的平均值
     * @param deviceList
     * @return
     */
    List<EnvironmentDataDTO> getEnvironmentData(@Param("deviceList") List<Integer> deviceList);
}
