package com.example.demos.web.pojo.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

//@TableName("weather_data")
@Data
public class WeatherData {
    @JsonProperty("code")
    private String code; // API返回的状态码

    @JsonProperty("updateTime")
    private String updateTime; // 更新时间

    @JsonProperty("fxLink")
    private String fxLink; // 一个链接到更详细信息的URL

    @JsonProperty("daily")
    private List<daily> weather; // 假设天气信息是一个列表
    public static class daily {
        @JsonProperty("fxDate")
        private String fxDate;
        @JsonProperty("tempMax")
        private String tempMax; // 温度，可能是一个字符串表示的温度范围
        @JsonProperty("tempMin")
        private String tempMin;
        @JsonProperty("textDay")
        private String textDay; // 白天天气类型，如"晴"、"雨"等
        @JsonProperty("textNight")
        private String textNight;//夜晚天气类型
        @JsonProperty("humidity")
        private String humidity;//相对湿度
        @JsonProperty("precip")
        private String precip;//降水量
    }
}