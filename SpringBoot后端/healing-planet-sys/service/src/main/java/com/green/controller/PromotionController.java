package com.green.controller;

import com.green.common.api.Result;
import com.green.entity.Promotion;
import com.green.service.IPromotionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/promotion")
public class PromotionController extends BaseController {

    @Resource
    private IPromotionService iPromotionService;

    /**
     * 获取广告集合
     * @return
     */
    @GetMapping("/all")
    public Result<List<Promotion>> list() {
        List<Promotion> list = iPromotionService.list();
        return Result.success(list);
    }

}
