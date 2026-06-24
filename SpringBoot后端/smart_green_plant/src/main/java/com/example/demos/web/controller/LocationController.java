package com.example.demos.web.controller;


import com.example.demos.web.common.result.Result;
import com.example.demos.web.pojo.dto.LocationByIpDTO;
import com.example.demos.web.pojo.dto.ReverseGeocodeDTO;
import com.example.demos.web.pojo.entity.LocationData;
import com.example.demos.web.service.LocationService;
import com.example.demos.web.utils.LocationUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/location")
@Slf4j
public class LocationController {
    @Autowired
    private LocationService locationService;

    /**
     * 普通ip定位
     */
    @GetMapping("/ip")
    public Result<LocationData> getLocationByIp(LocationByIpDTO locationByIpDTO, HttpServletRequest request) throws JsonProcessingException {
        // 获取客户端IP地址
        String clientIp = LocationUtil.getEffectiveIp(request);
        log.info("获取设备IP：{}", clientIp);

        // 调用服务层方法，并传递IP地址
        LocationData locationData = locationService.getLocationByIp(locationByIpDTO, clientIp);

        return Result.success(locationData);
    }

    /**
     * 全球逆地理编码
     * @param reverseGeocodeDTO
     * @return
     */
    @GetMapping("/geocode/reverse")
    public Result<LocationData> reverseGeocode(@RequestBody ReverseGeocodeDTO reverseGeocodeDTO){
        log.info("全球逆地理编码：{}",reverseGeocodeDTO);
        LocationData locationData = locationService.reverseGeocode(reverseGeocodeDTO);
        return Result.success(locationData);
    }
}
