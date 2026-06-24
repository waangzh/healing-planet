package com.example.demos.web.controller;

import com.example.demos.web.common.result.Result;
import com.example.demos.web.pojo.dto.PlantInstanceDTO;
import com.example.demos.web.pojo.entity.PlantInstance;
import com.example.demos.web.service.PlantInstanceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


//用户管理自己所种植的植物
@Slf4j//添加注解之后就不用自己定义log
@RestController
@RequestMapping("/plantinstance")
public class PlantInstanceController {

    @Autowired
    private PlantInstanceService plantInstanceService;


    /**
     * 根据用户id查询种植的植物
     *
     * @param userId
     * @return
     */
    @GetMapping("/{userId}")
    public Result<?> list(@PathVariable Integer userId) {
        log.info("查询用户种植的植物:{}",userId);
        return plantInstanceService.list(userId);
    }

    /**
     * 更新用户种植的绿植信息
     *
     * @param plantInstanceDTO
     * @return
     */
    @PostMapping("/update")
    public Result<?> updateById(@RequestBody PlantInstanceDTO plantInstanceDTO) throws JsonProcessingException {
        log.info("更新用户种植的绿植信息:{}", plantInstanceDTO);
        plantInstanceService.updateById(plantInstanceDTO);
        return Result.success();
    }

    /**
     * 添加新植物
     *
     * @param plantInstanceDTO
     * @return
     */
    @PostMapping("/add")
    public Result<?> add(@RequestBody PlantInstanceDTO plantInstanceDTO) throws Exception {
        log.info("新增种植绿植: {}", plantInstanceDTO);
        return plantInstanceService.add(plantInstanceDTO);
    }

    /**
     * 根据id单个删除
     *
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        return plantInstanceService.delete(id);
    }

    /**
     * 一键生成智能建议
     *
     * @param plantInstance
     * @return
     */
    @PostMapping("/getAdvice")
    public Result<?> generateAdvice(@RequestBody PlantInstance plantInstance) throws Exception {
        log.info("一键生成智能建议:{}", plantInstance);
        String ans = plantInstanceService.generateAdvice(plantInstance);
        return Result.success(ans);
    }

}


