package com.green.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.common.api.Result;
import com.green.dto.CollectDTO;
import com.green.vo.CollectVO;
import com.green.service.ICollectService;
import com.green.service.IUmsUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

import static com.green.security.jwt.JwtUtil.USER_NAME;


@RestController
@RequestMapping("/collect")
@Slf4j
public class CollectController {
    @Autowired
    private ICollectService collectService;
    @Autowired
    private IUmsUserService iUserService;
    /**
     * （取消）收藏文章
     * @param collectDTO
     * @return
     */
    @PostMapping
    public Result<?> collectPost(@RequestBody CollectDTO collectDTO) {
        log.info("收藏文章:{}", collectDTO);

        HashMap<String, Object> res = collectService.isCollected(collectDTO);

        return Result.success(res,(Boolean)res.get("isCollected")?"收藏成功":"取消收藏");
    }


    /**
     * 获取用户收藏列表
     * @return
     */
    @GetMapping("/getCollectList")
    public Result<Page<CollectVO>> getCollectList(@RequestParam String username,
                                                  @RequestParam(value = "pageNo", defaultValue = "1")  Integer pageNo,
                                                  @RequestParam(value = "size", defaultValue = "10") Integer pageSize) {
        log.info("获取用户收藏列表:{}", username);
        String userId = iUserService.getUserByUsername(username).getId();
        Page<CollectVO> list = collectService.list(new Page<>(pageNo, pageSize),userId);
        return Result.success(list);
    }

    /**
     * 验证是否收藏
     * @param postId
     * @return
     */
    @GetMapping("/validate")
    public Result<?> validateCollected(@RequestHeader(value = USER_NAME) String userName,@RequestParam String topicId) {
        log.info("验证是否收藏文章:{}",topicId);
        Boolean res = collectService.validate(userName,topicId);
        return Result.success(res);
    }
}
