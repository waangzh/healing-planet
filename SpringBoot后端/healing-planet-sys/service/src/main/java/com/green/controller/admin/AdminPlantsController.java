package com.green.controller.admin;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.common.api.Result;
import com.green.common.exception.ApiException;
import com.green.dto.PlantAliasDTO;
import com.green.dto.PlantDTO;
import com.green.service.IPlantAliasService;
import com.green.service.IPlantsService;
import com.green.vo.PlantAliasVO;
import com.green.vo.PlantsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/admin/plants")
@Slf4j
public class AdminPlantsController {

    @Autowired
    private IPlantsService plantsService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private IPlantAliasService plantAliasService;


    /**
     * 新增植物
     * @param plantDTO
     * @return
     */
    @PostMapping("/add")
    public Result<?> addPlant(@RequestBody PlantDTO plantDTO){
        log.info("添加植物:{}",plantDTO);
        plantsService.add(plantDTO);
        return Result.success();
    }

    /**
     * 分页查询植物信息库
     * @param pageNo
     * @param pageSize
     * @return
     */
    @GetMapping("/list")
    public Result<Page<PlantsVO>> list(@RequestParam(value = "pageNo", defaultValue = "1")  Integer pageNo,
                                       @RequestParam(value = "size", defaultValue = "10") Integer pageSize,
                                       @RequestParam(value = "key", defaultValue = "") String key){

        Page<PlantsVO> list = plantsService.getList(new Page<>(pageNo, pageSize),key);
        return Result.success(list);
    }

    /**
     * 根据绿植id查询
     * @param id
     * @return
     */
    @GetMapping
    public Result<PlantsVO> getPlantsById(@RequestParam(value = "id") String id) {
        log.info("根据绿植id查询:{}", id);
        PlantsVO plantsVO = null;

        try {
            // 1. 尝试从Redis获取
            String key = "Plants:" + id;
            plantsVO = (PlantsVO) redisTemplate.opsForValue().get(key);

            if (plantsVO == null) {
                // 2. Redis中没有则查数据库
                plantsVO = plantsService.getPlantsById(id);

                // 3. 写入Redis
                try {
                    redisTemplate.opsForValue().set(
                            key,
                            plantsVO,
                            30,
                            TimeUnit.MINUTES
                    );
                } catch (DataAccessException e) {
                    log.warn("Redis写入失败: {}", e.getMessage());
                }
            }
        } catch (RedisConnectionFailureException e) {
            // 4. Redis完全不可用时的降级处理
            log.error("Redis连接失败，降级到数据库查询", e);
            plantsVO = plantsService.getPlantsById(id);

        } catch (Exception e) {
            // 6. 其他异常处理
            log.error("查询绿植信息异常", e);
            throw new ApiException("查询失败");
        }

        return Result.success(plantsVO);
    }


    /**
     * 更新植物信息
     * @param plantDTO
     * @return
     */
    @PutMapping("/update")
    public Result<?> updateById(@RequestBody PlantDTO plantDTO){
        log.info("更新植物信息:{}",plantDTO);

        plantsService.updatePlants(plantDTO);
        return Result.success();
    }


    /**
     * 批量删除植物
     * @param ids
     * @return
     */
    @DeleteMapping("/delete")
    public Result<?> deleteByIds(@RequestParam List<String> ids){
        log.info("批量删除植物:{}",ids);
        plantsService.delete(ids);
        return Result.success();
    }

    @GetMapping("/{plantId}/aliases")
    public Result<List<PlantAliasVO>> listAliases(@PathVariable String plantId) {
        return Result.success(plantAliasService.listByPlantId(plantId));
    }

    @PostMapping("/{plantId}/aliases")
    public Result<?> addAlias(@PathVariable String plantId, @RequestBody PlantAliasDTO aliasDTO) {
        plantAliasService.add(plantId, aliasDTO);
        return Result.success();
    }

    @PutMapping("/{plantId}/aliases/{aliasId}")
    public Result<?> updateAlias(@PathVariable String plantId, @PathVariable String aliasId,
                                 @RequestBody PlantAliasDTO aliasDTO) {
        plantAliasService.update(plantId, aliasId, aliasDTO);
        return Result.success();
    }

    @DeleteMapping("/{plantId}/aliases/{aliasId}")
    public Result<?> deleteAlias(@PathVariable String plantId, @PathVariable String aliasId) {
        plantAliasService.delete(plantId, aliasId);
        return Result.success();
    }
}
