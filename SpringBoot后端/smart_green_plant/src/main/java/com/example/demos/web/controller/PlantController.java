package com.example.demos.web.controller;

import com.example.demos.web.common.result.Result;
import com.example.demos.web.pojo.dto.PlantDTO;
import com.example.demos.web.pojo.entity.Plant;
import com.example.demos.web.service.PlantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

//管理员管理所有植物
@Slf4j
@RestController
@RequestMapping("/plant")
public class PlantController {

    @Autowired
    private PlantService plantService;



    /**
     * 添加新植物
     * @param plantDTO
     * @return
     */
    @PostMapping("/add")
    public Result<?> add(@RequestBody PlantDTO plantDTO) {
        log.info("添加新植物{}", plantDTO);
        return plantService.save(plantDTO);
    }

    /**
     * 根据id查询植物
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result<?> selectById(@PathVariable Integer id){
        return plantService.selectById(id);
    }


    /**
     * 批量删除植物库信息
     * @param ids
     * @return
     */
    @DeleteMapping("/{ids}")
    public Result<?> delete(@PathVariable List<Integer> ids){
        log.info("删除,ids{}",ids);

        return plantService.delete(ids);
    }

    /**
     * 更新植物信息
     * @param Plant
     * @return
     */
    @PutMapping
    public  Result<?> update(@RequestBody Plant Plant){
        return plantService.update(Plant);
    }

    /**
     * 分页查询植物
     * @param pageNum
     * @param pageSize
     * @param search
     * @return
     */
    @GetMapping("/findPage")
    public Result<?> findPage(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "100") Integer pageSize,
                              @RequestParam(defaultValue = "") String search){
        return plantService.findPage(pageNum,pageSize,search);
    }

}