package com.example.demos.web.service;

import com.example.demos.web.pojo.entity.WeatherData;
import com.example.demos.web.pojo.entity.WeatherNow;
import reactor.core.publisher.Mono;

public interface WeatherDataService {
    /**
     * 三天天气预报
     * @param location
     * @return
     */
    Mono<WeatherData> getWeatherByCity(String cityName);

    /**
     * 实时天气预报
     * @param location
     * @return
     */
    Mono<WeatherNow> getWeatherNow(String location);
}
