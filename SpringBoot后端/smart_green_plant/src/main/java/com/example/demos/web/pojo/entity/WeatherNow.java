package com.example.demos.web.pojo.entity;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class WeatherNow {
    @JsonProperty("code")
    private String code; // API返回的状态码

    @JsonProperty("updateTime")
    private String updateTime; // 更新时间

    @JsonProperty("fxLink")
    private String fxLink; // 一个链接到更详细信息的URL

    @JsonProperty("now")
    private Now now;
    public static class  Now {
        @JsonProperty("obsTime")
        private String fxDate;
        @JsonProperty("temp")
        private String temp; // 温度，可能是一个字符串表示的温度范围
        @JsonProperty("windDir")
        private String windDir;
        @JsonProperty("humidity")
        private String humidity; // 相对湿度
        @JsonProperty("text")
        private String text;//白天天气类型，如"晴"、"雨"等
        @JsonProperty("precip")
        private String precip;//降水量
    }
}
