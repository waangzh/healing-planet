package com.example.demos.web.pojo.entity;

import lombok.Data;

import java.util.Map;
@Data
public class Content {

    private String deviceType;
    private String iotId;
    private String requestId;
    private Map<String, Object> checkFailedData;
    private String productKey;
    private long gmtCreate;
    private String deviceName;
    private Map<String, DeviceItems> items;


}

