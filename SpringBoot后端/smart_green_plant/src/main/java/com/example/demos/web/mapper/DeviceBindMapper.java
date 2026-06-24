package com.example.demos.web.mapper;


import com.example.demos.web.pojo.entity.BindingRecords;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DeviceBindMapper {

    @Insert("insert into binding_records (DEVICE_ID, DEVICE_KEY)values (#{deviceId},#{deviceKey})")
    void generateKeys(BindingRecords bindingRecords);


    /**
     * 查询是否存在这个uuid
     *
     * @param uuid
     * @return
     */
    @Select("select device_id from binding_records where device_key = #{uuid}")
    Integer findDeviceByKEY(String uuid);

    /**
     * 将后台用户，社区用户和设备绑定到一起
     * @param deviceId
     * @param backEndUserId
     * @param communityUserId
     */
    @Update("update binding_records set back_end_user_id = #{backEndUserId}, community_user_id = #{communityUserId} where device_id = #{deviceId}")
    void bindId(@Param("deviceId") Integer deviceId, @Param("backEndUserId") Integer backEndUserId, @Param("communityUserId") String communityUserId);

    /**
     * 检查一下该用户是否存在
     * @param communityUserId
     * @return
     */
    @Select("SELECT back_end_user_id from binding_records" +
            " where community_user_id=#{communityUserId}")
    List<Integer> existsCommunityById(String communityUserId);
}
