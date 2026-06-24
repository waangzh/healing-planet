package com.example.demos.web.service.impl;

import com.aliyun.iot20180120.models.QueryDevicePropertyStatusResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.demos.web.context.BaseContext;
import com.example.demos.web.exception.CustomException;
import com.example.demos.web.mapper.*;
import com.example.demos.web.pojo.dto.DeviceDto;
import com.example.demos.web.pojo.dto.ThresholdDto;
import com.example.demos.web.pojo.entity.*;
import com.example.demos.web.common.properties.AliIoTConfigProperties;
import com.example.demos.web.common.result.Result;
import com.example.demos.web.common.enums.FanStatus;
import com.example.demos.web.common.enums.LightStatus;
import com.example.demos.web.common.enums.PumpStatus;
import com.example.demos.web.pojo.vo.DeviceVO;
import com.example.demos.web.pojo.vo.ThresholdVO;
import com.example.demos.web.service.DeviceService;
import com.example.demos.web.service.UserMessageService;
import com.example.demos.web.utils.EmailUtil;
import com.example.demos.web.utils.QueryDevicePropertyUtil;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service

public class DeviceServiceImpl implements DeviceService {
    private static final Logger log = LoggerFactory.getLogger(DeviceServiceImpl.class);
    @Resource
    DeviceMapper deviceMapper;
    @Autowired
    private AliIoTConfigProperties aliIoTConfigProperties;
    @Autowired
    private EmailUtil emailUtil;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private QueryDevicePropertyUtil queryDevicePropertyUtil;
    @Autowired
    private UserMessageService userMessageService;

    @Autowired
    private DeviceBindMapper deviceBindMapper;

    @Autowired
    private PlantInstanceMapper plantInstanceMapper;

    // 使用线程安全的 Map 来存储每个设备的定时任务
    private final Map<Integer, ScheduledFuture<?>> deviceMonitoringTasks = new ConcurrentHashMap<>();

    // 创建任务调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    @Autowired
    private ThresholdMapper thresholdMapper;

    /**
     * 添加设备
     * @param device
     * @return
     */
    @Transactional
    public Result<?> addDevice(Device device){

        // 添加
        boolean result = deviceMapper.insert(device) > 0;

        if (result) {
            // 添加成功
            Integer devicedId = device.getId();
            UUID uuid = UUID.randomUUID();
            String deviceKey = uuid.toString();
            BindingRecords bindingRecords = new BindingRecords();
            bindingRecords.setDeviceId(devicedId);
            bindingRecords.setDeviceKey(deviceKey);
            deviceBindMapper.generateKeys(bindingRecords);
            //在中间表plant_instance中插入记录
            PlantInstance plantInstance = PlantInstance.builder()
                    .deviceId(devicedId)
                    .build();
            plantInstanceMapper.insertData(plantInstance);

            log.info("当前插入的值:{}",bindingRecords);

            return Result.success(deviceKey);
        } else {
            // 添加失败
            return Result.error("添加失败");
        }
    }

    /**
     * 删除设备
     * @param ids
     * @return
     */
    public Result<?> deleteDevice(List<Integer> ids){
        deviceMapper.deleteBatchIds(ids);
        return Result.success();
    }

    /**
     * 更新设备信息
     * @param device
     * @return
     */
    public Result<?> updateDevice(Device device){
        boolean result = deviceMapper.updateById(device) > 0;
        if (result) {
            return Result.success();
        }
        else{
            return Result.error("Have nothing to update.");
        }
    }


    /**
     * 根据id搜索设备
     * @param id
     * @return
     */
    public Result<?> selectById(Integer id){
        return Result.success(deviceMapper.selectById(id));
    }

    /**
     * 获取设备的运行状态
     * @param deviceName
     * @return
     */
    @SneakyThrows
    public Result<?> getDeviceStatus(String deviceName){
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                // 请确保代码运行环境设置了环境变量 ALIBABA_CLOUD_ACCESS_KEY_ID 和 ALIBABA_CLOUD_ACCESS_KEY_SECRET。
                .setAccessKeyId(aliIoTConfigProperties.getAccessKeyId())
                .setAccessKeySecret(aliIoTConfigProperties.getAccessKeySecret())
                // Endpoint 请参考 https://api.aliyun.com/product/Iot
                .setEndpoint("iot.cn-shanghai.aliyuncs.com");
        com.aliyun.iot20180120.Client client = new com.aliyun.iot20180120.Client(config);
        com.aliyun.iot20180120.models.GetDeviceStatusRequest getDeviceStatusRequest = new com.aliyun.iot20180120.models.GetDeviceStatusRequest();
        getDeviceStatusRequest.setDeviceName(deviceName);
        getDeviceStatusRequest.setIotInstanceId(aliIoTConfigProperties.getIotInstanceId());
        getDeviceStatusRequest.setProductKey(aliIoTConfigProperties.getProductKey());

        com.aliyun.iot20180120.models.GetDeviceStatusResponse getDeviceStatusResponse = client.getDeviceStatus(getDeviceStatusRequest);

        String status = getDeviceStatusResponse.body.data.getStatus();

        return Result.success(status);
    }

    /**
     * 查询设备属性
     * @param deviceName
     * @return
     */
    @SneakyThrows
    public Result<?> found(String deviceName) {

        QueryDevicePropertyStatusResponse queryDevicePropertyStatusResponse =
                queryDevicePropertyUtil.query(deviceName);


        return Result.success(queryDevicePropertyStatusResponse);
    }


    /**
     * 获取最新环境数据，用于判断是否需要灌溉等
     * @param device
     * @return
     */
    @SneakyThrows
    public List<Float> getLatestValue(Device device){
        QueryDevicePropertyStatusResponse queryDevicePropertyStatusResponse = queryDevicePropertyUtil.query(device.getName());
        //return Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(1).value);
        List<Float> environmentValue = new ArrayList<>();
        // 土壤湿度
        environmentValue.add(Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(1).value));
        // 二氧化碳浓度
        environmentValue.add(Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(3).value));
        // 光照强度
        environmentValue.add(Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(4).value));
        // 温度
        environmentValue.add(Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(0).value));
        // 环境湿度
        environmentValue.add(Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(2).value));
        // 水泵
        environmentValue.add(Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(5).value));
        // 灯光
        environmentValue.add(Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(7).value));
        // 风扇
        environmentValue.add(Float.parseFloat(queryDevicePropertyStatusResponse.body.data.getList().propertyStatusInfo.get(13).value));

        return environmentValue;
    }

    /**
     * 自动控制
     * @param device
     * @return
     */
    public Result<?> AutoControl(Device device){
        log.info("设备状态:{}",getDeviceStatus(device.getName()).getData() );
        if(getDeviceStatus(device.getName()).getData().equals("OFFLINE") ){
            throw new CustomException("-1","当前设备离线，无法控制开关！");
        }
        List<Float> environmentValue = getLatestValue(device);
        LambdaQueryWrapper<Threshold> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Threshold::getDeviceId, device.getId());
        Threshold threshold = thresholdMapper.selectOne(queryWrapper);
        if(environmentValue.get(0) < threshold.getSoilMoistureMin()){
            device.setIrriogationPumpStatus(PumpStatus.PUMP_ON);
        }else if(environmentValue.get(0) > threshold.getSoilMoistureMax()){
            device.setIrriogationPumpStatus(PumpStatus.PUMP_OFF);
        }
        if(environmentValue.get(1) < threshold.getCo2Min()){
            device.setFanSwitch(FanStatus.ON);
        }else if(environmentValue.get(1) > threshold.getCo2Max()){
            device.setFanSwitch(FanStatus.OFF);
        }
        if(environmentValue.get(2) < threshold.getLightIntensityMin()){
            device.setLightStatus(LightStatus.ON);
        }else if(environmentValue.get(2) > threshold.getLightIntensityMax()){
            device.setLightStatus(LightStatus.OFF);
        }
        //设置各个开关状态
        log.info("自动设置:{}",device.getName());
        DeviceDto deviceDto = new DeviceDto();
        BeanUtils.copyProperties(device, deviceDto);
        setSwitch(deviceDto);
        return Result.success();
    }


    /**
     * 根据植物实例id查询设备名称
     * @param plant_instance_id
     * @return
     */
    public Result<?> getDeviceName(Integer plant_instance_id) {
        String name = deviceMapper.getDeviceName(plant_instance_id);
        return Result.success(name);
    }

    /**
     * 设置设备预警状态
     * @Device device
     */
    @Override
    public void setWarning(Device device) {
        //更新设备预警状态
        deviceMapper.updateById(device);
        Integer status = device.getWarningStatus();
        Integer deviceId = device.getId();
        // 根据状态启动或停止监测任务
        if (status == 1) {
            startMonitoringTask(deviceId);
        } else if (status == 0) {
            stopMonitoringTask(deviceId);
        }
    }

    /**
     * 设置设备开关
     * @param deviceDto
     */
    @SneakyThrows
    @Override
    public void setSwitch(DeviceDto deviceDto) {
        log.info("设备状态:{}",getDeviceStatus(deviceDto.getName()).getData() );
        if(getDeviceStatus(deviceDto.getName()).getData().equals("OFFLINE") ){
            throw new CustomException("-1","当前设备离线，无法控制开关！");
        }
        Device device = new Device();
        BeanUtils.copyProperties(deviceDto, device);
        device.setTimestamp(LocalDateTime.now());
        log.info("设置设备开关-set:{}",device);
        boolean result = deviceMapper.updateById(device) > 0;
        if (result) {

            com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                    .setAccessKeyId(aliIoTConfigProperties.getAccessKeyId())
                    .setAccessKeySecret(aliIoTConfigProperties.getAccessKeySecret())
                    // Endpoint 请参考 https://api.aliyun.com/product/Iot
                    .setEndpoint("iot.cn-shanghai.aliyuncs.com");
            com.aliyun.iot20180120.Client client = null;
            try {
                client = new com.aliyun.iot20180120.Client(config);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            com.aliyun.iot20180120.models.SetDevicePropertyRequest setDevicePropertyRequest = new com.aliyun.iot20180120.models.SetDevicePropertyRequest();
            setDevicePropertyRequest.setDeviceName(device.getName());
            setDevicePropertyRequest.setIotInstanceId(aliIoTConfigProperties.getIotInstanceId());
            setDevicePropertyRequest.setProductKey(aliIoTConfigProperties.getProductKey());
            //水泵开关、灯光开关、风扇开关、wifi开关
            setDevicePropertyRequest.setItems(
                    "{\"LightStatus\":"+"\""+device.getLightStatus().getValue()+"\""+","
                    +"\"IrrigationPumpStatus\":"+"\""+device.getIrriogationPumpStatus().getValue()+"\""+","
                    +"\"FanSwitch\":"+"\""+device.getFanSwitch().getValue()+"\""+","
                    +"\"WifiSwitch\":"+"\""+device.getWifiSwitch().getValue()+"\""//+","
                    +"}");
            log.info("设置开关:{}",setDevicePropertyRequest.getItems());
            com.aliyun.iot20180120.models.SetDevicePropertyResponse setDevicePropertyResponse = client.setDeviceProperty(setDevicePropertyRequest);
        }
    }

    /**
     * 设置设备阈值
     * @param thresholdDto
     */
    @Override
    public void setThresold(ThresholdDto thresholdDto) {
        LambdaQueryWrapper<Threshold> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Threshold::getDeviceId, thresholdDto.getDeviceId());
        Threshold query = thresholdMapper.selectOne(queryWrapper);
        Threshold threshold = new Threshold();
        BeanUtils.copyProperties(thresholdDto, threshold);
        threshold.setCreatedTime(LocalDateTime.now());
        boolean result = false;
        if(query == null) {
            threshold.setUpdatedTime(LocalDateTime.now());
            thresholdMapper.insert(threshold);
        }else {
            threshold.setId(query.getId());
            threshold.setUpdatedTime(LocalDateTime.now());
            result = thresholdMapper.updateById(threshold) > 0;
        }
        if (result) {
            log.info("向平台发送阈值设置");
            com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                    .setAccessKeyId(aliIoTConfigProperties.getAccessKeyId())
                    .setAccessKeySecret(aliIoTConfigProperties.getAccessKeySecret())
                    // Endpoint 请参考 https://api.aliyun.com/product/Iot
                    .setEndpoint("iot.cn-shanghai.aliyuncs.com");
            com.aliyun.iot20180120.Client client = null;
            try {
                client = new com.aliyun.iot20180120.Client(config);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            com.aliyun.iot20180120.models.SetDevicePropertyRequest setDevicePropertyRequest = new com.aliyun.iot20180120.models.SetDevicePropertyRequest();
            setDevicePropertyRequest.setDeviceName(deviceMapper.selectById(threshold.getDeviceId()).getName());
            setDevicePropertyRequest.setIotInstanceId(aliIoTConfigProperties.getIotInstanceId());
            setDevicePropertyRequest.setProductKey(aliIoTConfigProperties.getProductKey());
            //水泵开关、灯光开关、风扇开关、wifi开关
            setDevicePropertyRequest.setItems(
                    "{\"CO2ValueMax\":"+threshold.getCo2Max()+","
                            +"\"SoilMoistureMax\":"+threshold.getSoilMoistureMax()+","
                            +"\"EnvironmentHumidityMax\":"+threshold.getHumidityMax()+","
                            +"\"LightLuxMax\":"+threshold.getLightIntensityMax()+","
                            +"\"CO2ValueMin\":"+threshold.getCo2Min()+","
                            +"\"SoilMoistureMin\":"+threshold.getSoilMoistureMin()+","
                            +"\"EnvironmentHumidityMin\":"+threshold.getHumidityMin()+","
                            +"\"LightLuxMin\":"+threshold.getLightIntensityMin()+","
                            +"\"TemperatureMax\":"+threshold.getTemperatureMax()+","
                            +"\"TemperatureMin\":"+threshold.getTemperatureMin()
                            +"}");
            try {
                com.aliyun.iot20180120.models.SetDevicePropertyResponse setDevicePropertyResponse = client.setDeviceProperty(setDevicePropertyRequest);
            } catch (Exception e) {
                throw new CustomException("设置设备阈值失败，请重新设置","0");
            }
        }
    }

    /**
     * 查询设备阈值
     * @param deviceId
     * @return
     */
    @Override
    public ThresholdVO getThresold(Integer deviceId) {
        LambdaQueryWrapper<Threshold> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Threshold::getDeviceId, deviceId);
        Threshold threshold = thresholdMapper.selectOne(queryWrapper);
        if(threshold == null){
            throw new CustomException("当前设备阈值为空，请先设置阈值!","-1");
        }
        ThresholdVO thresholdVO = new ThresholdVO();
        BeanUtils.copyProperties(threshold,thresholdVO);
        return thresholdVO;
    }

    /**
     * 根据用户id获取设备信息
     * @param userId
     * @return
     */
    @Override
    public List<DeviceVO> selectByUserId(Integer userId) {
        LambdaQueryWrapper<Device> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Device::getUserId, userId);
        List<Device> query = deviceMapper.selectList(queryWrapper);
        if(query == null) {
            throw new CustomException("当前用户没有绑定设备","-1");
        }
        List<DeviceVO> deviceVOList = new ArrayList<>();
        for(Device device : query) {
            DeviceVO deviceVO = new DeviceVO();
            BeanUtils.copyProperties(device, deviceVO);
            deviceVOList.add(deviceVO);
        }
        return deviceVOList;
    }

    /**
     * 启动设备的监测任务
     * @param deviceId 设备ID
     */
    private void  startMonitoringTask(Integer deviceId) {
        // 如果设备已经有监测任务，先停止旧任务
        stopMonitoringTask(deviceId);
        log.info("当前用户：{}，启动监测",BaseContext.getCurrentId());
        Long userId = BaseContext.getCurrentId();
        // 创建新的监测任务
        Runnable monitoringTask = () -> {
            // 执行监测逻辑
            List<Integer> warningStatus = checkEnvironmentData(deviceId);
            if (!warningStatus.isEmpty()) {
                handleAbnormalData(deviceId,warningStatus,userId);
            }
        };

        // 每5秒执行一次监测任务
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(monitoringTask, 0, 30000, TimeUnit.SECONDS);

        // 将任务保存到 Map 中
        deviceMonitoringTasks.put(deviceId, task);
    }

    /**
     * 停止设备的监测任务
     * @param deviceId 设备ID
     */
    private void stopMonitoringTask(Integer deviceId) {
        ScheduledFuture<?> task = deviceMonitoringTasks.get(deviceId);
        if (task != null) {
            task.cancel(true); // 取消任务
            deviceMonitoringTasks.remove(deviceId); // 从 Map 中移除任务
        }
    }

    /**
     * 检查设备的环境数据是否异常
     * @param deviceId
     * @return List<Integer>
     */
    private List<Integer> checkEnvironmentData(Integer deviceId) {
        // 检查
        log.info("正在监测设备 {} 的环境数据...",deviceId);
        Device device = deviceMapper.selectById(deviceId);

        List<Float> environmentValue = getLatestValue(device);
        List<Integer> warningStatus = new ArrayList<>();

        LambdaQueryWrapper<Threshold> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Threshold::getDeviceId, deviceId);
        Threshold threshold = thresholdMapper.selectOne(queryWrapper);
        //土壤湿度
        if(environmentValue.get(0) < threshold.getSoilMoistureMin()){
            warningStatus.add(-1);
        }else if(environmentValue.get(0) > threshold.getSoilMoistureMax()){
            warningStatus.add(1);
        }else {
            warningStatus.add(0);
        }
        //二氧化碳
        if(environmentValue.get(1) < threshold.getCo2Min()){
            warningStatus.add(-1);
        }else if(environmentValue.get(1) > threshold.getCo2Max()){
            warningStatus.add(1);
        }else{
            warningStatus.add(0);
        }
        //光照强度
        if(environmentValue.get(2) < threshold.getLightIntensityMin()){
            warningStatus.add(-1);

        }else if(environmentValue.get(2) > threshold.getLightIntensityMax()){
            warningStatus.add(1);
        }else{
            warningStatus.add(1);
        }
        return warningStatus;
    }

    /**
     * 处理设备异常数据
     * @param deviceId 设备ID
     */
    private void handleAbnormalData(Integer deviceId,List<Integer> warningStatus,Long userId) {
        // 实现异常处理逻辑
        // 例如：发送警报通知，记录日志
        log.info("设备 {} 环境数据 {} 异常，请处理！",deviceId,warningStatus);
        String subject = "绿植状态预警";
        String deviceName = deviceMapper.selectById(deviceId).getName();
        String text = "绿植状态预警！\n设备："+deviceName+"\n当前状态：\n";
        //获取当前用户的email
        log.info("发送报警信息给当前用户为：{}",userId);
        User user = userMapper.selectById(userId);
        String email = user.getEmail();
        if(warningStatus.get(0) == -1){
            text+="土壤湿度过低，请及时浇水！\n";
        }else if(warningStatus.get(0) == 1){
            text+="土壤湿度过高，请关闭水泵！\n";
        }
        if(warningStatus.get(1) == -1){
            text+="二氧化碳含量过低，请及时通风！\n";
        }else if(warningStatus.get(1) == 1){
            text+="二氧化碳含量过高，请及时通风！\n";
        }
        if(warningStatus.get(2) == -1){
            text+="光照强度过低，请及时补光！\n";
        }else if(warningStatus.get(2) == 1){
            text+="光照强度过高，请关闭灯光！\n";
        }

        userMessageService.createWarningMessage(userId,subject,text);

        emailUtil.sendSimpleEmail(email, subject, text);
    }
}
