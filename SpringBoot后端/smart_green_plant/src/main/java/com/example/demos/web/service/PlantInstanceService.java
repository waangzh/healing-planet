package com.example.demos.web.service;

import com.example.demos.web.common.result.Result;
import com.example.demos.web.pojo.dto.PlantInstanceDTO;
import com.example.demos.web.pojo.entity.PlantInstance;
import com.fasterxml.jackson.core.JsonProcessingException;

public interface PlantInstanceService {

    /**
     * 添加新植物
     * @param plantInstanceDTO
     * @return
     */
    Result<?> add(PlantInstanceDTO plantInstanceDTO) throws Exception;
    /**
     * 根据id单个删除
     * @param id
     * @return
     */
    Result<?> delete(Long id);
    /**
     * 查询种植的植物
     * @param userId
     * @return
     */
    Result<?> list(Integer userId);

    /**
     * 一键生成智能问答
     * @param plantInstance
     * @return
     */
    String generateAdvice(PlantInstance plantInstance) throws Exception;

    /**
     * 更新用户种植的绿植信息
     * @param plantInstanceDTO
     */
    void updateById(PlantInstanceDTO plantInstanceDTO) throws JsonProcessingException;
}
