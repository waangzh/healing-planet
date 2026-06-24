package com.example.demos.web.service.impl;

import com.example.demos.web.mapper.LocationMapper;
import com.example.demos.web.pojo.dto.LocationByIpDTO;
import com.example.demos.web.pojo.dto.ReverseGeocodeDTO;
import com.example.demos.web.pojo.entity.LocationData;
import com.example.demos.web.service.LocationService;
import com.example.demos.web.utils.HttpClientUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;


@Service
@Slf4j
public class LocationServiceImpl implements LocationService {
    @Autowired
    private LocationMapper locationMapper;

    private static final String BAIDU_LOCATION_IP_URL = "https://api.map.baidu.com/location/ip";
    private static final String BAIDU_REVERSEGEOCODE_URL = "https://api.map.baidu.com/reverse_geocoding/v3";
    @Value("${baidu.ak}")
    private String ak;

    /**
     * 使用ip地址进行定位处理
     * @param locationByIpDTO
     * @param clientIp
     * @return
     * @throws JsonProcessingException
     */
    @Override
    public LocationData getLocationByIp(LocationByIpDTO locationByIpDTO, String clientIp) throws JsonProcessingException {
        Map<String, String> params = new HashMap<>();
        params.put("ak", ak);
        params.put("coor", "bd09ll");
        if (locationByIpDTO.getIp() != null && !locationByIpDTO.getIp().isEmpty()) {
            params.put("ip", locationByIpDTO.getIp());
        } else {
            params.put("ip", clientIp);
        }
        log.info("当前ip为：{}", params.get("ip"));
        String result = HttpClientUtil.doGet(BAIDU_LOCATION_IP_URL, params);
        //解码unicode编码
        String decodedResult = StringEscapeUtils.unescapeJson(result);
        System.out.println(decodedResult);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(decodedResult);
        // 提取根级别的address
        String rootAddress = rootNode.path("address").asText();
        System.out.println("根级别的address: " + rootAddress);

        // 提取content.address
        String contentAddress = rootNode.path("content").path("address").asText();

        // 提取content.address_detail.address
        String city = rootNode.path("content").path("address_detail").path("city").asText();
        String province = rootNode.path("content").path("address_detail").path("province").asText();

        // 提取经度和纬度
        String longitude = rootNode.path("content").path("point").path("x").asText();
        String latitude = rootNode.path("content").path("point").path("y").asText();
        String location = latitude + "," + longitude;//纬度在前，经度在后

        LocationData locationData = new LocationData();

        BeanUtils.copyProperties(locationByIpDTO, locationData);
        locationData.setAddress(contentAddress);
        locationData.setCity(city);
        locationData.setProvince(province);
        locationData.setLocation(location);
        System.out.println(locationData);

        LocationData temp = locationMapper.getLocationByDeviceId(locationData.getDeviceId());
        if (temp == null) {
            locationMapper.insertLocation(locationData);
        } else {
            locationMapper.updateLocation(locationData);
        }
        return locationData;
    }


    /**
     * 全球逆地理编码，利用经纬度获取地理位置
     * @param reverseGeocodeDTO
     * @return
     */
    @Override
    @Transactional
    public LocationData reverseGeocode(ReverseGeocodeDTO reverseGeocodeDTO) {
        //构造请求
        Map<String, String> params = new HashMap<>();
        params.put("ak", ak);
        String location = reverseGeocodeDTO.getLatitude() + "," + reverseGeocodeDTO.getLongitude();
        params.put("location", location);
        params.put("output", "json");
        params.put("coordtype", "wgs84ll");
        params.put("extensions_poi", "0");
        //发送请求
        String result = HttpClientUtil.doGet(BAIDU_REVERSEGEOCODE_URL, params);
        //log.info(result);

        LocationData locationData;
        //封装定位数据
        try {
            // 使用 Jackson 解析 JSON 字符串
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(result);
            JsonNode locationNode = rootNode.path("result");
            // 提取经纬度数据
            String lng = locationNode.path("location").path("lng").asText().replace("\"", ""); // 去掉双引号
            String lat = locationNode.path("location").path("lat").asText().replace("\"", ""); // 去掉双引号
            String latAndLng = lat + "," + lng; // 纬度在前，经度在后

            // 获取地址、城市和省份并去掉双引号
            String address = locationNode.path("formatted_address").asText().replace("\"", "");
            String city = locationNode.path("addressComponent").path("city").asText().replace("\"", "");
            String province = locationNode.path("addressComponent").path("province").asText().replace("\"", "");
            // 将数据封装到 LocationData 对象
            locationData = LocationData.builder()
                    .deviceId(reverseGeocodeDTO.getDeviceId())
                    .location(latAndLng)
                    .city(city)
                    .province(province)
                    .address(address)
                    .build();

        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON: ", e);
            return null;
        }

        //将封装好的数据存储到数据库当中
        LocationData temp = locationMapper.getLocationByDeviceId(locationData.getDeviceId());
        if (temp == null) {
            locationMapper.insertLocation(locationData);//不存在则插入
        } else {
            locationMapper.updateLocation(locationData);//存在则更新
        }
        return locationData;
    }
}
