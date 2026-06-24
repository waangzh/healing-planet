package com.example.demos.web.service;

import com.example.demos.web.pojo.dto.LocationByIpDTO;
import com.example.demos.web.pojo.dto.ReverseGeocodeDTO;
import com.example.demos.web.pojo.entity.LocationData;
import com.fasterxml.jackson.core.JsonProcessingException;

public interface LocationService {
    /**
     * 通过ip获取定位
     *
     * @param locationByIpDTO
     * @param clientIp
     * @return
     */
    LocationData getLocationByIp(LocationByIpDTO locationByIpDTO, String clientIp) throws JsonProcessingException;

    /**
     * 全球逆地理编码，通过经纬度获取地理位置
     * @param reverseGeocodeDTO
     * @return
     */
    LocationData reverseGeocode(ReverseGeocodeDTO reverseGeocodeDTO);
}
