package com.example.demos.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demos.web.common.result.Result;
import com.example.demos.web.pojo.dto.PlantDTO;
import com.example.demos.web.pojo.entity.Plant;
import com.example.demos.web.mapper.PlantMapper;
import com.example.demos.web.service.PlantService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class PlantServiceImpl implements PlantService {
    @Autowired
    PlantMapper plantMapper;

    /**
     * 添加新植物
     * @param plant
     * @return
     */
    @Override
    public Result<?> save(PlantDTO plantDTO) {
        // 避免重复
        Plant existingPlant = plantMapper.selectOne(
                new LambdaQueryWrapper<Plant>().eq(Plant::getName, plantDTO.getName())
        );

        if (existingPlant != null) {
            // 如果已存在
            return Result.error("当前绿植种类已存在");
        }

        // 添加
        Plant plant = new Plant();
        BeanUtils.copyProperties(plantDTO, plant);
        boolean result = plantMapper.insert(plant) > 0;

        if (result) {
            // 添加成功
            return Result.success();
        } else {
            // 添加失败
            return Result.error("Failed to add the plant.");
        }
    }

    /**
     * 根据id查询
     * @param id
     * @return
     */
    @Override
    public Result<?> selectById(Integer id){
        Plant plant = plantMapper.selectById(id);
        return Result.success(plant);
    }

    /**
     * 根据id删除
     * @param ids
     * @return
     */
    @Override
    public Result<?> delete(List<Integer> ids){
        plantMapper.deleteBatchIds(ids);
        return Result.success();
    }

    /**
     * 更新植物库
     * @param Plant
     * @return
     */
    @Override
    public  Result<?> update(Plant Plant){
        plantMapper.updateById(Plant);
        return Result.success();
    }

    /**
     * 分页查询植物库
     * @param pageNum
     * @param pageSize
     * @param search
     * @return
     */
    @Override
    public Result<?> findPage(Integer pageNum, Integer pageSize, String search){
        /*pageNum（页码），pageSize（每页大小），search（搜索关键词）*/
        LambdaQueryWrapper<Plant> wrappers = Wrappers.<Plant>lambdaQuery();
        if(StringUtils.isNotBlank(search)){
            wrappers.like(Plant::getName,search);
        }
        Page<Plant> plantPage = plantMapper.selectPage(new Page<>(pageNum,pageSize), wrappers);
        /*records：当前页的数据列表，类型为List<Plant>。这个列表包含了根据查询条件和分页参数从数据库中检索出的Plant对象。*/
        //返回当前页的页码、每页的总记录数，以及当前页的数据列表等
        return Result.success(plantPage);
    }

}
