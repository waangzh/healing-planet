package com.green.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.entity.Plants;
import com.green.vo.PlantsVO;
import org.springframework.stereotype.Repository;

@Repository
public interface PlantsMapper extends BaseMapper<Plants> {
    /**
     * 分页查询植物信息库
     * @param page
     * @param key
     * @return
     */
    Page<PlantsVO> selectListAndPage(Page<Object> page, String key);


    PlantsVO selectById(String id);
}
