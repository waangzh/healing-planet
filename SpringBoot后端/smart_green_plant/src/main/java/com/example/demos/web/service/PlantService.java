package com.example.demos.web.service;

import com.example.demos.web.common.result.Result;
import com.example.demos.web.pojo.dto.PlantDTO;
import com.example.demos.web.pojo.entity.Plant;

import java.util.List;

public interface PlantService {


    /**
     * 添加新植物
     * @param plantDTO
     * @return
     */
    Result<?> save(PlantDTO plantDTO);

    /**
     * 根据id查询植物
     * @param id
     * @return
     */
    Result<?> selectById(Integer id);

    /**
     * 批量删除植物库信息
     * @param ids
     * @return
     */
    Result<?> delete(List<Integer> ids);

    /**
     * 更新植物信息
     * @param Plant
     * @return
     */
    Result<?> update(Plant Plant);

    /**
     * 分页查询植物
     * @param pageNum
     * @param pageSize
     * @param search
     * @return
     */
    Result<?> findPage(Integer pageNum, Integer pageSize, String search);

}
