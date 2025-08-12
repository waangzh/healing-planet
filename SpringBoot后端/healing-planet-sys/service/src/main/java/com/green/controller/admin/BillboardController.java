package com.green.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.green.common.api.Result;
import com.green.controller.BaseController;
import com.green.entity.Billboard;
import com.green.service.IBillboardService;
import com.green.vo.BillboardVO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/admin/billboard")
@Slf4j
public class BillboardController extends BaseController {

    @Resource
    private IBillboardService iBillboardService;

    /**
     * 获取最新公告
     * @return
     */
    @GetMapping("/show")
    public Result<Billboard> getNotices(){
        log.info("获取允许展示的公告");
        List<Billboard> list = iBillboardService.list(new
                LambdaQueryWrapper<Billboard>().eq(Billboard::isShow,true));
        return Result.success(list.get(list.size()- 1));
    }


    /**
     * 获取所有公告
     * @return
     */
    @GetMapping("/all")
    public Result<?> getAll(){
        log.info("获取所有公告");
        List<Billboard> voList = iBillboardService.list();
        return Result.success(voList);
    }


    /**
     * 修改公告
     * @param billboard
     * @return
     */
    @PostMapping("/update")
    public Result<?> update(@RequestBody Billboard billboard){
        log.info("修改公告:{}", billboard);
        iBillboardService.updateById(billboard);
        return Result.success();
    }

    /**
     * 删除公告
     * @param ids
     * @return
     */
    @DeleteMapping("/delete")
    public Result<?> delete(@RequestParam List<Integer> ids){
        iBillboardService.removeByIds(ids);
        return Result.success();
    }

    /**
     * 新增公告
     * @param billboard
     * @return
     */
    @PostMapping("/add")
    public Result<?> add(@RequestBody Billboard billboard){
        billboard.setModifyTime(new Date());
        iBillboardService.save(billboard);
        return Result.success();
    }


    /**
     * 根据id查询公告
     * @param id
     * @return
     */
    @GetMapping
    public Result<?> getById(@RequestParam Integer id){
        log.info("根据id查询公告:{}",id);
        return Result.success(iBillboardService.getById(id));
    }

}
