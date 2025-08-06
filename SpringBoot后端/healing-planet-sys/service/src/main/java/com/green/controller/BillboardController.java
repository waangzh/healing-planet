package com.green.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.green.common.api.Result;
import com.green.entity.Billboard;
import com.green.service.IBillboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/billboard")
public class BillboardController extends BaseController {

    @Resource
    private IBillboardService iBillboardService;

    /**
     * 获取最新公告
     * @return
     */
    @GetMapping("/show")
    public Result<Billboard> getNotices(){
        List<Billboard> list = iBillboardService.list(new
                LambdaQueryWrapper<Billboard>().eq(Billboard::isShow,true));
        return Result.success(list.get(list.size()- 1));
    }
}
