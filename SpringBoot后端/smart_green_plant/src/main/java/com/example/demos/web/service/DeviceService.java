package com.example.demos.web.service;

import com.example.demos.web.common.result.Result;
import com.example.demos.web.pojo.dto.DeviceDto;
import com.example.demos.web.pojo.dto.ThresholdDto;
import com.example.demos.web.pojo.entity.Device;
import com.example.demos.web.pojo.entity.Threshold;
import com.example.demos.web.pojo.vo.DeviceVO;
import com.example.demos.web.pojo.vo.ThresholdVO;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

public interface DeviceService {

    /**
     * 添加设备
     * @param device
     * @return
     */
    Result<?> addDevice(Device device);

    /**
     * 添加设备
     * @param ids
     * @return
     */
    Result<?> deleteDevice(List<Integer> ids);

    /**
     * 更新设备信息
     * @param device
     * @return
     */
    Result<?> updateDevice(Device device);


    /**
     * 根据id搜索设备
     * @param id
     * @return
     */
    Result<?> selectById(Integer id);

    /**
     * 获取设备状态  OFFLINE(离线)/ONLINE(在线)
     * @param deviceName
     * @return
     */
    Result<?> getDeviceStatus(String deviceName);

    /**
     * 查询设备属性
     * @param deviceName
     * @return
     */
    Result<?> found(String deviceName);

    /**
     * 获取最新环境数据
     * @param device
     * @return
     */
    List<Float> getLatestValue(Device device);

    /**
     * 自动控制
     * @param device
     * @return
     */
    Result<?> AutoControl(Device device);

    /**
     * 通过植物实例id查询设备名称
     * @param plant_instance_id
     * @return
     */
    Result<?> getDeviceName(@PathVariable Integer plant_instance_id);

    /**
     * 设置设备预警状态
     * @param device
     */
    void setWarning(Device device);

    /**
     * 设置设备开关
     * @param deviceDto
     */
    void setSwitch(DeviceDto deviceDto) throws Exception;

    /**
     * 设置设备阈值
     * @param thresholdDto
     */
    void setThresold(ThresholdDto thresholdDto);

    /**
     * 查询设备阈值
     * @param deviceId
     * @return
     */
    ThresholdVO getThresold(Integer deviceId);

    /**
     * 根据用户id获取设备信息
     * @param userId
     * @return
     */
    List<DeviceVO> selectByUserId(Integer userId);
}
