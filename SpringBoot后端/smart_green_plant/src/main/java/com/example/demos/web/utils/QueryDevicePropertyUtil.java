package com.example.demos.web.utils;

import com.aliyun.iot20180120.models.QueryDevicePropertyStatusRequest;
import com.aliyun.iot20180120.models.QueryDevicePropertyStatusResponse;
import com.example.demos.web.common.properties.AliIoTConfigProperties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class QueryDevicePropertyUtil {

    @Autowired
    private AliIoTConfigProperties aliIoTConfigProperties;

    /**
     * 查询aliyun平台设备属性
     * @param deviceName
     * @return
     */
    @SneakyThrows
    public  QueryDevicePropertyStatusResponse query(String deviceName){

        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(aliIoTConfigProperties.getAccessKeyId())
                .setAccessKeySecret(aliIoTConfigProperties.getAccessKeySecret())
                // Endpoint 请参考 https://api.aliyun.com/product/Iot
                .setEndpoint("iot.cn-shanghai.aliyuncs.com");
        com.aliyun.iot20180120.Client client = null;
        try {
            client = new com.aliyun.iot20180120.Client(config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        QueryDevicePropertyStatusRequest queryDevicePropertyStatusRequest = new QueryDevicePropertyStatusRequest();
        log.info("查询aliyun设备属性:{}", deviceName);

        queryDevicePropertyStatusRequest.setDeviceName(deviceName);
        queryDevicePropertyStatusRequest.setIotInstanceId(aliIoTConfigProperties.getIotInstanceId());
        queryDevicePropertyStatusRequest.setProductKey(aliIoTConfigProperties.getProductKey());
        QueryDevicePropertyStatusResponse queryDevicePropertyStatusResponse = client.queryDevicePropertyStatus(queryDevicePropertyStatusRequest);


        return queryDevicePropertyStatusResponse;
    }


}
