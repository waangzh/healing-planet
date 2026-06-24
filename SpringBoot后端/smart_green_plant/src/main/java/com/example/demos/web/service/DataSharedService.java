package com.example.demos.web.service;


import org.json.JSONException;

import java.util.List;

public interface DataSharedService {
    /**
     * 社区获取用户种植的植物
     * @param communityUserId
     * @return
     */
    List<String> getPlantNames(String communityUserId);

    /**
     * 大模型写日志接口
     *
     * @param communityUserId
     * @param message
     * @return
     */
    String writePost(String communityUserId, String message) throws Exception;
}
