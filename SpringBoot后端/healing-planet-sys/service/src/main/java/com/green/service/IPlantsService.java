package com.green.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.green.dto.PlantDTO;
import com.green.entity.Plants;
import com.green.vo.PlantsVO;

import java.util.List;

public interface IPlantsService extends IService<Plants> {
    /**
     * 分页查询植物信息库
     * @param objectPage
     * @return
     */
    Page<PlantsVO> getList(Page<Object> objectPage, String key);

    /**
     * 根据绿植id查询
     * @param id
     * @return
     */
    PlantsVO getPlantsById(String id);

    /**
     * 添加植物
     * @param plantDTO
     */
    void add(PlantDTO plantDTO);

    /**
     * 识别植物
     * @param imgUrl
     * @return
     */
    String identify(String imgUrl);

    /**
     * 更新植物信息
     * @param plantDTO
     */
    void updatePlants(PlantDTO plantDTO);

    /**
     * 批量删除植物
     * @param ids
     */
    void delete(List<String> ids);

}
