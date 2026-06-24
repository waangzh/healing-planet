package com.example.demos.web.service.impl;

import com.example.demos.web.pojo.entity.WeatherData;
import com.example.demos.web.pojo.entity.WeatherNow;
import com.example.demos.web.service.WeatherDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Service
public class WeatherDataImpl implements WeatherDataService {
    @Value("${weather.api.key}")
    private String apiKey;
    @Value("${weather.api.now.key}")
    private String apiNowKey;
    //@Value("${weather.api.base-url}")
    //private String baseUrl;

    private final WebClient webClient = WebClient.create();
    @Override
    public Mono<WeatherData> getWeatherByCity(String location) {
        //异步
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("devapi.qweather.com")
                        .path("/v7/weather/3d")//三天的天气数据
                        .queryParam("location", location)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(WeatherData.class);
    }

    @Override
    public Mono<WeatherNow> getWeatherNow(String location) {
        //异步
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("devapi.qweather.com")
                        .path("/v7/weather/now")//实时数据
                        .queryParam("location", location)
                        .queryParam("key", apiNowKey)
                        .build())
                .retrieve()
                .bodyToMono(WeatherNow.class);
    }
}
