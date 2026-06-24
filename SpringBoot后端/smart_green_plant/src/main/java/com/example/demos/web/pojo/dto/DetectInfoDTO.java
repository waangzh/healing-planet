package com.example.demos.web.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class DetectInfoDTO {
    Integer plantId;//植物id
    Integer deviceId;//设备id
}

