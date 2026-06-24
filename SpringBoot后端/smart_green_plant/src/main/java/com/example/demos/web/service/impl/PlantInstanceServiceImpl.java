package com.example.demos.web.service.impl;


import com.aliyun.iot20180120.models.QueryDevicePropertyStatusResponse;
import com.aliyun.iot20180120.models.RegisterDeviceRequest;
import com.aliyun.iot20180120.models.RegisterDeviceResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.demos.web.common.properties.AliIoTConfigProperties;
import com.example.demos.web.common.properties.AliOssProperties;
import com.example.demos.web.common.result.Result;
import com.example.demos.web.context.BaseContext;
import com.example.demos.web.mapper.DeviceMapper;
import com.example.demos.web.mapper.LocationMapper;
import com.example.demos.web.mapper.PlantMapper;
import com.example.demos.web.pojo.dto.LocationByIpDTO;
import com.example.demos.web.pojo.dto.PlantInstanceDTO;
import com.example.demos.web.pojo.entity.*;
import com.example.demos.web.mapper.PlantInstanceMapper;
import com.example.demos.web.pojo.vo.PlantInstanceVO;
import com.example.demos.web.service.LocationService;
import com.example.demos.web.service.PlantInstanceService;
import com.example.demos.web.utils.BaiDuUtil;
import com.example.demos.web.utils.QueryDevicePropertyUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.sql.Wrapper;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class PlantInstanceServiceImpl implements PlantInstanceService {
    @Autowired
    PlantInstanceMapper plantInstanceMapper;
    @Autowired
    private PlantMapper plantMapper;
    @Autowired
    QueryDevicePropertyUtil queryDevicePropertyUtil;
    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    BaiDuUtil baiDuUtil;
    @Autowired
    private LocationMapper locationMapper;
    @Autowired
    private LocationService locationService;
    @Autowired
    private AliIoTConfigProperties aliIoTConfigProperties;

    /**
     * 根据用户id查询绿植
     * @param userId
     * @return
     */
    @Override
    public Result<?> list(Integer userId){
        //List<PlantInstance> plantInstances = plantInstanceMapper.list(userId);
        QueryWrapper<PlantInstance> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        List<PlantInstance> plantInstances = plantInstanceMapper.selectList(queryWrapper);
        //log.info("查询种植的植物: {}", plantInstances);
        if(plantInstances.isEmpty()){
            return Result.success(Collections.emptyList());
        }

        // 批量查询 plantId,locationId,deviceId
        Set<Integer> plantIds = plantInstances.stream().map(PlantInstance::getPlantId).collect(Collectors.toSet());
        Set<Integer> locationIds = plantInstances.stream().map(PlantInstance::getLocationId).collect(Collectors.toSet());
        Set<Integer> deviceIds = plantInstances.stream().map(PlantInstance::getDeviceId).collect(Collectors.toSet());

        // select id, name from plant where id in (plantId, ...)
        // 转化为用map存储,每次查询变成 O(1)

        Map<Integer, String> plantNameMap = plantMapper.selectBatchIds(plantIds)
                .stream().collect(Collectors.toMap(Plant::getId, Plant::getName));
        // select id, address from location_data where id in (locationId, ...)
        Map<Integer, String> locationMap = locationMapper.selectBatchIds(locationIds)
                .stream().collect(Collectors.toMap(LocationData::getId, LocationData::getAddress));
        // select id, name from device where id in (deviceId, ...)
        Map<Integer, String> deviceMap = deviceMapper.selectBatchIds(deviceIds)
                .stream().collect(Collectors.toMap(Device::getId, Device::getName));

        // VO数组
        List<PlantInstanceVO> plantInstanceVOList = plantInstances.stream().map(plantInstance ->
                PlantInstanceVO.builder()
                        .id(plantInstance.getId())
                        .plantId(plantInstance.getPlantId())
                        .plantName(plantNameMap.get(plantInstance.getPlantId()))
                        .location(locationMap.get(plantInstance.getLocationId()))
                        .deviceName(deviceMap.get(plantInstance.getDeviceId()))
                        .datePlanted(plantInstance.getDatePlanted())
                        .imgUrl(plantInstance.getImgUrl())
                        .build()
        ).collect(Collectors.toList());


        //List<PlantInstanceVO> plantInstanceVOList = new ArrayList<>();
        //for (PlantInstance plantInstance : plantInstances) {
        //    String plantName = plantMapper.selectById(plantInstance.getPlantId()).getName();
        //    String location = locationMapper.selectById(plantInstance.getLocationId()).getAddress();
        //    String deviceName = deviceMapper.selectById(plantInstance.getDeviceId()).getName();
        //    PlantInstanceVO plantInstanceVO = PlantInstanceVO.builder()
        //            .id(plantInstance.getId())
        //            .plantId(plantInstance.getPlantId())
        //            .plantName(plantName)
        //            .location(location)
        //            .deviceName(deviceName)
        //            .datePlanted(plantInstance.getDatePlanted())
        //            .build();
        //    plantInstanceVOList.add(plantInstanceVO);
        //}
        return Result.success(plantInstanceVOList);
    }

    /**
     * 一键生成智能问答
     * @param plantInstance
     * @return
     */
    @Override
    public String generateAdvice(PlantInstance plantInstance){
        String plantName = plantMapper.selectById(plantInstance.getPlantId()).getName();
        PlantInstance plantInstance1 = plantInstanceMapper.selectById(plantInstance.getId());
        QueryDevicePropertyStatusResponse queryDevicePropertyStatusResponse = queryDevicePropertyUtil.query(deviceMapper.getDeviceName(plantInstance.getId()));
        //温度、土壤湿度、空气湿度、二氧化碳浓度、光照强度
        Float temperature = Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(0).value);
        Float soilMoisture = Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(1).value);
        Float environmentHumidity = Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(2).value);
        Float co2Concentration = Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(3).value);
        Float lightIntensity = Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(4).value);
        List<String> imgUrl = new ArrayList<>();
        imgUrl.add(plantInstance1.getImgUrl());
        String location = "湖北省宜昌市";
        String advice = "";
        try {
            advice = baiDuUtil.generatePlantCareAdvice(plantName,temperature,soilMoisture,environmentHumidity,co2Concentration,lightIntensity,location,imgUrl);
            // 解析 JSON
            JSONObject outerJson = new JSONObject(advice);
            //log.info("outerjson:{}",outerJson);
            // 获取响应
            JSONArray choices = outerJson.getJSONArray("choices");
            if (choices.length() > 0) {
                JSONObject firstChoice = choices.getJSONObject(0);
                JSONObject message = firstChoice.getJSONObject("message");
                String result = message.getString("content");
                return result;
            } else {
                throw new RuntimeException("API响应中没有choices内容");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "生成养护建议时出错: " + e.getMessage();
        }
    }


    /**
     * 更新用户种植的绿植信息
     * @param plantInstanceDTO
     */
    @Override
    public void updateById(PlantInstanceDTO plantInstanceDTO) throws JsonProcessingException {
        PlantInstance plantInstance = plantInstanceMapper.selectById(plantInstanceDTO.getId());
        //更新定位信息
        LocationByIpDTO locationByIpDTO = LocationByIpDTO.builder()
                .ip(plantInstanceDTO.getIp())
                .deviceId(plantInstance.getDeviceId())
                .build();
        LocationData locationData = locationService.getLocationByIp(locationByIpDTO,"");
        //更新设备名称
        Device device = deviceMapper.selectById(plantInstance.getDeviceId());
        device.setName(plantInstanceDTO.getDeviceName());
        //更新植物名称
        plantInstance.setPlantId(plantInstanceDTO.getPlantId());

        //更新
        locationMapper.updateById(locationData);
        deviceMapper.updateById(device);
        plantInstanceMapper.updateById(plantInstance);
    }

    /**
     * 添加新植物
     * @param plantInstanceDTO
     * @return
     */
    @Override
    @Transactional
    public Result<?> add(PlantInstanceDTO plantInstanceDTO) throws Exception{

            // 用户id
            Integer userId = BaseContext.getCurrentId().intValue();

            PlantInstance plantInstance = PlantInstance.builder()
                    .plantId(plantInstanceDTO.getPlantId()) // 对应绿植库中绿植id
                    .datePlanted(LocalDateTime.now()) // 种植时间
                    .userId(userId)
                    .imgUrl(plantInstanceDTO.getImg())
                    .deviceId(0) // todo 处理问题：添加植物实例需要有设备id，但是添加设备有需要实例id，二者不能同时添加
                    .build();


            // 添加绿植实例
            plantInstanceMapper.insert(plantInstance);
            Integer plantInstanceId = plantInstance.getId();
            // 添加对应设备
            com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                    // 请确保代码运行环境设置了环境变量 ALIBABA_CLOUD_ACCESS_KEY_ID 和 ALIBABA_CLOUD_ACCESS_KEY_SECRET。
                    .setAccessKeyId(aliIoTConfigProperties.getAccessKeyId())
                    .setAccessKeySecret(aliIoTConfigProperties.getAccessKeySecret())
                    // Endpoint 请参考 https://api.aliyun.com/product/Iot
                    .setEndpoint("iot.cn-shanghai.aliyuncs.com");
            com.aliyun.iot20180120.Client client = new com.aliyun.iot20180120.Client(config);

            // 注册新设备（添加新绿植对应的监测设备）
            com.aliyun.iot20180120.models.RegisterDeviceRequest registerDeviceRequest = new com.aliyun.iot20180120.models.RegisterDeviceRequest();
            registerDeviceRequest.setDeviceName(plantInstanceDTO.getDeviceName()); // 设备名称
            registerDeviceRequest.setIotInstanceId(aliIoTConfigProperties.getIotInstanceId());
            registerDeviceRequest.setProductKey(aliIoTConfigProperties.getProductKey());

            RegisterDeviceResponse registerDeviceResponse = client.registerDevice(registerDeviceRequest);
            System.out.println("新增设备:"+ registerDeviceResponse.toString());
            // 如果设备注册失败，抛出异常
            if (!registerDeviceResponse.body.success) {
                // 只有抛出运行时异常才会回滚
                throw new RuntimeException(registerDeviceResponse.body.errorMessage);
            }
            Device device = Device.builder()
                    .name(plantInstanceDTO.getDeviceName())
                    .plantInstanceId(plantInstanceId)
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .build();
            deviceMapper.insert(device);
            Integer deviceId = device.getId();
            //设置设备id
            plantInstance.setDeviceId(deviceId);
            plantInstanceMapper.updateById(plantInstance);

            // 添加成功
            return Result.success();

    }

    //删除植物实例时，需要确认，否则会连带一起删除与该植物相关的环境数据
    //根据植物实例id删除
    @Override
    public Result<?> delete(Long id){
        PlantInstance del = plantInstanceMapper.selectById(id);
        plantInstanceMapper.deleteById(id);
        return Result.success();
    }


}
