package com.example.demos.web.service;


import org.json.JSONException;

import java.util.List;
import java.util.Map;

public interface DataSharedService {
    /**
     * 社区获取用户种植的植物
     * @param communityUserId
     * @return
     */
    List<String> getPlantNames(String communityUserId);

    /**
     * 获取社区用户在花盆系统中的账号和植物实例，供状态感知 RAG 使用。
     */
    Map<String, Object> getRagContext(String communityUserId);

    /**
     * 大模型写日志接口
     *
     * @param communityUserId
     * @param message
     * @return
     */
    String writePost(String communityUserId, String message) throws Exception;
}
