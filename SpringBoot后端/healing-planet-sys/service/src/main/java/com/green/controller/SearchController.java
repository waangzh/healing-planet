package com.green.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.common.api.Result;
import com.green.vo.PostVO;
import com.green.service.IPostService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/search")
public class SearchController extends BaseController {

    @Resource
    private IPostService postService;

    /**
     * 关键字检索
     * @param keyword
     * @param pageNum
     * @param pageSize
     * @return
     */
    @GetMapping
    public Result<Page<PostVO>> searchList(@RequestParam("keyword") String keyword,
                                           @RequestParam("pageNum") Integer pageNum,
                                           @RequestParam("pageSize") Integer pageSize) {
        Page<PostVO> results = postService.searchByKey(keyword, new Page<>(pageNum, pageSize));
        return Result.success(results);
    }

}
