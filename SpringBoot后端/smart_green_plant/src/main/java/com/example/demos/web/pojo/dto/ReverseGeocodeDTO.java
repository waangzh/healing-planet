package com.example.demos.web.pojo.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReverseGeocodeDTO {
    String latitude;//纬度
    String longitude;//经度
    Integer deviceId;//设备id
}
