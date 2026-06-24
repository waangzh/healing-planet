package com.example.demos.web.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LocationByIpDTO {
    private String ip;//网络ip
    private Integer deviceId;//设备id号
}
