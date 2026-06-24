package com.example.demos.web.controller;

import com.example.demos.web.pojo.entity.WeatherData;
import com.example.demos.web.pojo.entity.WeatherNow;
import com.example.demos.web.service.WeatherDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping
public class WeatherDataController {
    @Autowired
    private WeatherDataService weatherDataService;

    /**
     * 三天天气预报
     * @param location
     * @return
     */
    @GetMapping("/weather")
    public Mono<WeatherData> getWeatherByCity(String location) {
        location = "101200901";
        log.info("天气预报:宜昌");
        return weatherDataService.getWeatherByCity(location);
    }

    /**
     * 实时天气预报
     * @param location
     * @return
     */
    @GetMapping("/weatherNow")
    public Mono<WeatherNow> getWeatherNow(String location) {
        location = "101200901";
        log.info("天气预报:宜昌");
        return weatherDataService.getWeatherNow(location);
    }
}
