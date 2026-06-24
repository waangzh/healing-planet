package com.example.demos.web.pojo.dto;

import com.example.demos.web.common.enums.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceDto {
    private Integer id;
    private String name;//设备名称
    private Integer warningStatus; //预警状态
    private PumpStatus irriogationPumpStatus;//水泵开关状态（1在线/0离线）
    private LightStatus lightStatus;//灯光开关状态（1在线/0离线）
    private FanStatus fanSwitch;//风扇开关
    private WifiStatus wifiSwitch;//wifi开关
}
