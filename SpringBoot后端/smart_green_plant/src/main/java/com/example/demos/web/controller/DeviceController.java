package com.example.demos.web.controller;

import com.example.demos.web.common.result.Result;
import com.example.demos.web.pojo.dto.DeviceDto;
import com.example.demos.web.pojo.dto.ThresholdDto;
import com.example.demos.web.pojo.entity.Device;
import com.example.demos.web.pojo.vo.DeviceVO;
import com.example.demos.web.pojo.vo.ThresholdVO;
import com.example.demos.web.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/device")
@Slf4j
public class DeviceController {
    @Autowired
    private DeviceService deviceService;

    /**
     * 添加设备
     * @param device
     * @return
     */
    @PostMapping
    public Result<?> addDevice(@RequestBody Device device){
        log.info("添加设备:{}",device);
        return deviceService.addDevice(device);
    }

    /**
     * 删除设备
     * @param ids
     * @return
     */
    @DeleteMapping("/{ids}")
    public Result<?> deleteDevice(@PathVariable List<Integer> ids){
        log.info("删除设备:{}",ids);
        return deviceService.deleteDevice(ids);
    }

    /**
     * 更新设备信息
     * @param device
     * @return
     */
    @PutMapping
    public Result<?> updateDevice(@RequestBody Device device){
        log.info("更新设备信息:{}",device);
        return deviceService.updateDevice(device);
    }


    /**
     * 根据id搜索设备
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result<?> selectById(@PathVariable Integer id){
        log.info("根据id查询设备:{}",id);
        return deviceService.selectById(id);
    }

    /**
     * 获取设备状态  OFFLINE(离线)/ONLINE(在线)
     * @param deviceName
     * @return
     */
    @GetMapping("/status/{deviceName}")
    public Result<?> getDeviceStatus(@PathVariable String deviceName){
        log.info("获取设备状态:{}",deviceName);
        return deviceService.getDeviceStatus(deviceName);
    }

    /**
     * 设置设备阈值
     * @param thresholdDto
     * @return
     * @throws Exception
     */
    @PutMapping("/setThreshold")
    public Result<?> setThresold(@RequestBody ThresholdDto thresholdDto) throws Exception {
        log.info("设置设备阈值:{}",thresholdDto);
        deviceService.setThresold(thresholdDto);
        return Result.success();
    }

    /**
     * 根据设备id查询设备阈值
     * @param deviceId
     * @return
     */
    @GetMapping("/getThreshold")
    public Result<?> getThresold(@RequestParam Integer deviceId){
        log.info("查询设备阈值:{}",deviceId);
        ThresholdVO thresholdVO = deviceService.getThresold(deviceId);
        return Result.success(thresholdVO);
    }

    /**
     * 设置设备开关
     * @param deviceDto
     * @return
     * @throws Exception
     */
    @PutMapping("/setSwitch")
    public Result<?> setSwitch(@RequestBody DeviceDto deviceDto) throws Exception {
        log.info("设置设备开关:{}",deviceDto);
        deviceService.setSwitch(deviceDto);
        return Result.success();
    }

    /**
     * 查询设备属性
     * @param deviceName
     * @return
     */
    @GetMapping("/found/{deviceName}")
    public Result<?> found(@PathVariable String deviceName){
        log.info("查询设备属性:{}",deviceName);
        return deviceService.found(deviceName);
    }


    /**
     * 自动控制
     * @param device
     * @return
     */
    @PostMapping("/setAuto")
    public Result<?> AutoControl(@RequestBody Device device){
        log.info("自动控制:{}",device);
        return deviceService.AutoControl(device);
    }

    /**
     * 通过植物实例id查询设备名称
     * @param plant_instance_id
     * @return
     */
    @GetMapping("/getDeviceName/{plant_instance_id}")
    public Result<?> getDeviceName(@PathVariable Integer plant_instance_id){
        log.info("根据用户种植绿植的id查询设备名称:{}",plant_instance_id);
        return deviceService.getDeviceName(plant_instance_id);
    }

    /**
     * 设置设备预警状态
     * @param device
     * @return
     */
    @PostMapping("/warning")
    public Result<?> setWarning(@RequestBody Device device){
        log.info("设置预警状态：{}",device);
        deviceService.setWarning(device);
        return Result.success();
    }

    /**
     * 根据用户id查询设备
     * @return
     */
    @GetMapping("/getDevice")
    public Result<?> getDeviceListByUserId(@RequestParam Integer userId){
        log.info("根据用户id获取设备信息:{}",userId);
        List<DeviceVO> list = deviceService.selectByUserId(userId);
        return Result.success(list);
    }


}
