package com.example.demos.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum FanStatus {
    OFF(0,"关闭"),
    ON(1,"打开");

    @EnumValue
    private final Integer value;
    @JsonValue
    private final String desc;

    FanStatus(Integer value,String desc){
        this.value = value;
        this.desc = desc;
    }
}
