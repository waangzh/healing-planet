package com.example.demos.web.pojo.entity;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备地理位置
 * @TableName location
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LocationData implements Serializable {
    /**
     * 主键id
     */
    private Integer id;
    /**
     * 设备关联id
     */
    private Integer deviceId;
    /**
     * 详细地址
     */
    private String address;

    /**
     * 经纬度,经度在前，纬度在后
     */
    private String location;

    private String province;

    private String city;

}