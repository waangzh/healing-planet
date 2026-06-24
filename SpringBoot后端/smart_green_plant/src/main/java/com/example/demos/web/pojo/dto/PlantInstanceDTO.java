package com.example.demos.web.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlantInstanceDTO {
    private Integer id;
    /**
     * 植物种类id
     */
    private Integer plantId;
    /**
     * 设备名称
     */
    private String deviceName;
    /**
     * 绿植图片
     */
    private String img;
    private String ip;
}

