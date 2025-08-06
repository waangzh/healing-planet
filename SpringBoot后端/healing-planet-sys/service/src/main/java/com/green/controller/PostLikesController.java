package com.green.controller;

import com.green.common.api.Result;
import com.green.entity.User;
import com.green.vo.PostLikesVO;
import com.green.service.IPostLikesService;
import com.green.service.IUmsUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

import static com.green.jwt.JwtUtil.USER_NAME;


@RestController
@RequestMapping("/like")
@Slf4j
public class PostLikesController extends BaseController {

    @Autowired
    private IPostLikesService postLikesService;
    @Resource
    private IUmsUserService userService;

    /**
     * 文章是否点赞
     * @param userName
     * @param topicId
     * @return
     */
    @PostMapping("/post/{topicId}")
    public Result<PostLikesVO> toggleLike(@RequestHeader(value = USER_NAME) String userName, @PathVariable String topicId) {
        log.info("文章是否点赞:{}",topicId);
        User user = userService.getUserByUsername(userName);
        PostLikesVO vo = postLikesService.togglePostLike(topicId, user.getId());
        Boolean isLiked = vo.getIsLiked();
        return Result.success(vo,isLiked ? "点赞成功" : "已取消点赞");
    }


    /**
     * 验证是否点赞
     * @param userName
     * @param postId
     * @return
     */
    @GetMapping("/validate")
    public Result<?> validate(@RequestHeader(value = USER_NAME) String userName,@RequestParam String topicId) {
        log.info("验证用户{}是否点赞:{}",userName,topicId);
        Boolean res = postLikesService.validate(userName,topicId);
        return Result.success(res);
    }

}
