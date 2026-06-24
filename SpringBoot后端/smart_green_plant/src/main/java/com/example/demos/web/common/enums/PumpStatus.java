package com.example.demos.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum PumpStatus {
    PUMP_OFF(0,"关闭"),
    PUMP_ON(1,"开启");

    @EnumValue//将value映射到数据库
    private final int value;
    @JsonValue//返回给前端的数据
    private final String desc;

    PumpStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
