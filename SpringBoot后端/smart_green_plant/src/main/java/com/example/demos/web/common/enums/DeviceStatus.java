package com.example.demos.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum DeviceStatus {
    ONLINE(1,"在线"),
    OFFLINE(0,"离线");

    @EnumValue//将value映射到数据库
    private final int value;
    @JsonValue//返回
    private final String desc;

    DeviceStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
