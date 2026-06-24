package com.example.demos.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum WifiStatus {
    OFFLINE(0,"关闭"),
    ONLINE(1,"打开");

    @EnumValue
    private final Integer value;
    @JsonValue
    private final String desc;

    WifiStatus(Integer value,String desc){
        this.value = value;
        this.desc = desc;
    }
}
