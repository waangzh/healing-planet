package com.example.demos.web.pojo.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.example.demos.web.common.enums.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceVO {
    private Integer id;
    private String name;//设备名称
    private Integer warningStatus; //预警状态
    private Integer plantInstanceId;//监测植物id
    private DeviceStatus online;//设备在线状态 1在线/0离线
    //Integer switchStatus;//设备开关状态
    private PumpStatus irriogationPumpStatus;//水泵开关状态（1在线/0离线）
    private LightStatus lightStatus;//灯光开关状态（1在线/0离线）
    private FanStatus fanSwitch;//风扇开关
    private WifiStatus wifiSwitch;//wifi开关
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;//数据采集时间
}
