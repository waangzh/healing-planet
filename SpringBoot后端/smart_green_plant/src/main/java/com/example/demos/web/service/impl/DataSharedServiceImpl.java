package com.example.demos.web.service.impl;

import com.example.demos.web.mapper.DataSharedMapper;
import com.example.demos.web.pojo.dto.EnvironmentDataDTO;
import com.example.demos.web.service.DataSharedService;
import com.example.demos.web.utils.BaiDuUtil;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class DataSharedServiceImpl implements DataSharedService {

    @Autowired
    private DataSharedMapper dataSharedMapper;
    @Autowired
    private BaiDuUtil baiDuUtil;



    /**
     * 社区获取用户种植的植物
     * @param communityUserId
     * @return
     */
    @Override
    public List<String> getPlantNames(String communityUserId) {

        return dataSharedMapper.getPlantName(communityUserId);
    }

    @Override
    public Map<String, Object> getRagContext(String communityUserId) {
        Integer backEndUserId = dataSharedMapper.getBackEndUserId(communityUserId);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("linked", backEndUserId != null);
        context.put("backEndUserId", backEndUserId);
        context.put("plants", backEndUserId == null
                ? java.util.Collections.emptyList()
                : dataSharedMapper.getRagPlants(communityUserId));
        return context;
    }

    @Override
    public String writePost(String communityUserId, String message) throws Exception {
        //根据前端返回的id查询后台所购置的机器的熟练以及里面种植的植物
        List<Integer> deviceList = dataSharedMapper.getDeviceById(communityUserId);
        log.info("我所绑定的设备有:{}",deviceList);
        //查询完之后，我需要知道，我种植的作物，当天各种环境数据的平均值，以及我种植的植物等信息，完成一篇日志的撰写
        List<EnvironmentDataDTO> environmentDataDTOList = dataSharedMapper.getEnvironmentData(deviceList);
        log.info("环境数据：{}",environmentDataDTOList);

        String s = baiDuUtil.writePost(environmentDataDTOList, message);
        log.info("百度返回的数据：{}",s);
        return s;

    }
}
