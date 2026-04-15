package com.green.controller;

import com.green.common.api.Result;
import com.green.entity.User;
import com.green.service.IUmsUserService;
import com.green.service.RecommendationService;
import com.green.vo.RecommendPostVO;
import com.green.vo.UserBindDeviceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

import static com.green.security.jwt.JwtUtil.USER_NAME;

@RestController
@Slf4j
@RequestMapping
public class RecommendationController {
    @Autowired
    RecommendationService recommendationService;

    @Autowired
    IUmsUserService iUmsUserService;

    @Autowired
    RedisTemplate redisTemplate;

    public static final String POSTS_KEY = "recommend_posts_key";
    public static final String USER_KEY = "recommend_users_key";

    // 通过设备密钥绑定社区用户
    @PostMapping("/userBindDevice")
    public Result<UserBindDeviceVO> userBindDevice(@RequestParam String deviceKey,
                                                   @RequestHeader(value = USER_NAME) String userName) throws IOException {
        log.info("绑定设备参数：deviceKey={}, userName={}", deviceKey, userName);
        User communityUser = iUmsUserService.getUserByUsername(userName);
        UserBindDeviceVO userBindDeviceVO = recommendationService.userBindDevice(deviceKey, communityUser);
        return Result.success(userBindDeviceVO);
    }

    // 推荐文章：支持前端“换一换”（递增 pageNo）
    @GetMapping("/recommend/posts")
    public Result<List<RecommendPostVO>> postsRecommendation(@RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                                             @RequestParam(value = "size", defaultValue = "8") Integer pageSize,
                                                             @RequestHeader(value = USER_NAME, defaultValue = "zmjkk") String userName) throws Exception {
        log.info("文章推荐分页查询：pageNo={}, pageSize={}", pageNo, pageSize);
        User communityUser = iUmsUserService.getUserByUsername(userName);
        log.info("文章推荐用户信息：{}", communityUser);

        String object = (String) redisTemplate.opsForValue().get(POSTS_KEY + communityUser.getId());
        if (object == null || object.isEmpty()) {
            log.info("文章推荐缓存不存在，触发大模型生成推荐");
            recommendationService.LLMRecommendationPosts(communityUser);
        }

        List<RecommendPostVO> postList = recommendationService.getPostsRecommendations(pageNo, pageSize, communityUser);
        return Result.success(postList);
    }

    // 推荐用户：支持前端“换一换”（递增 pageNo）
    @GetMapping("/recommend/users")
    public Result<List<User>> usersRecommendateion(@RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                                   @RequestParam(value = "size", defaultValue = "5") Integer pageSize,
                                                   @RequestHeader(value = USER_NAME, defaultValue = "zmjkk") String userName) throws Exception {
        log.info("用户推荐分页查询：pageNo={}, pageSize={}", pageNo, pageSize);
        User communityUser = iUmsUserService.getUserByUsername(userName);
        log.info("用户推荐用户信息：{}", communityUser);

        String object = (String) redisTemplate.opsForValue().get(USER_KEY + communityUser.getId());
        if (object == null || object.isEmpty()) {
            recommendationService.LLMRecommendationUsers(communityUser);
        }

        List<User> userList = recommendationService.getUsersRecommendations(pageNo, pageSize, communityUser);
        return Result.success(userList);
    }

    @PostMapping("/writePost")
    public Result<String> postWriting(@RequestHeader(value = USER_NAME) String userName, @RequestBody String message) throws IOException {
        User communityUser = iUmsUserService.getUserByUsername(userName);
        log.info("写日志用户信息：{}, 输入内容：{}", communityUser, message);
        String post = recommendationService.writePost(communityUser, message);
        return Result.success(post);
    }
}
