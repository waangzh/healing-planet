package com.green.controller;


import com.green.common.api.Result;
import com.green.entity.User;
import com.green.vo.RecommendPostVO;
import com.green.vo.UserBindDeviceVO;
import com.green.service.IUmsUserService;
import com.green.service.RecommendationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

import static com.green.security.jwt.JwtUtil.USER_NAME;

/*
推荐分为两个推荐，一个是推荐文章，一个是推荐用户（为了减轻大模型的负担，尽可能少一点数据）
但是感觉两个可以一起做推荐，因为推荐取材的东西都非常的相似，如果多次去取的话可能会有些浪费

推荐文章的：
1. 用户的喜欢和收藏
2. 用户浏览过的帖子，几个有关的标签，一个是绿植养殖，绿植购买建议。
2.1 还需要根据帖子的浏览量啊，这些内容来做推荐。
2.2 这个绿植购买建议又可以细分，因为我们这个社区主要就是分享心得和交友用的，这个建议又可以打标签，但是由于时间有限，无法设计出来功能和逻辑都十分完备的系统，所以说现在还是以先实现和展示为主就可以了。
3. 用户是否购买过我们的产品，如果是... ，如果不是又怎么样
4. （复杂一点）根据用户经常聊天的用户，如果聊天近七天天数>=3，打上火花，火炬这类标签，的用户他相关的创作的内容和标签吧。（这个怎么给用户打标签暂定）

在提一嘴展示页面，包括搜索和推荐，就是要做到什么样子呢？每次刷新都不一样才可以啊，就是尽可能模拟出来那种随机的活的内容而不死的内容。

还有一些吊东西，就是什么，能不能让语音去操控一些东西：
比如说：和这个进行花盆进行对话，就能让他给后端发请求，然后总结到这个用户的客户端页面
实现这种智能互动，其也会更加更加的好展示，以及好怎么说，好写文档？
就是体现设计嘛，就是不知道到时候能不能实现。
*/

/**
 * 推荐内容相关接口
 */
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

    /**
     * 通过设备KEY，绑定社区用户
     *
     * @param deviceKey
     * @param userName
     * @return
     */
    @PostMapping("/userBindDevice")
    public Result<UserBindDeviceVO> userBindDevice(@RequestParam String deviceKey,
                                                   @RequestHeader(value = USER_NAME) String userName) throws IOException {
        log.info("绑定传递信息：deviceKey:{}\n,userName{}", deviceKey, userName);
        User communityUser = iUmsUserService.getUserByUsername(userName);
        UserBindDeviceVO userBindDeviceVO = recommendationService.userBindDevice(deviceKey, communityUser);
        return Result.success(userBindDeviceVO);
    }

//    /**
//     * 猜你喜欢界面，使用大模型预测
//     *
//     * @param userName
//     * @return
//     */
//    @GetMapping("/posts/LLMRecommendation")
//    public Result<?> postsFromLLM(@RequestParam(value = USER_NAME) String userName) throws Exception {
//
//        User communityUser = iUmsUserService.getUserByUsername(userName);
//        Object object = redisTemplate.opsForValue().get(POSTS_KEY + communityUser.getId());
//
//        if (object != null) {
//            return Result.success();
//        }
//
//        log.info("大语言模型推荐文章接口，用户具体信息:{}", communityUser);
//        recommendationService.LLMRecommendationPosts(communityUser);
//        return Result.success();
//    }



    /**
     * 文章推荐接口
     *
     * @param userName
     * @return
     */
    @GetMapping("/recommend/posts")
    public Result<List<RecommendPostVO>> postsRecommendation(@RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                                             @RequestParam(value = "size", defaultValue = "5") Integer pageSize,
                                                             @RequestHeader(value = USER_NAME , defaultValue = "zmjkk") String userName) throws Exception {


        log.info("文章推荐分页查询：当前页pageNo{},一页多少条消息pageSize{}", pageNo, pageSize);
        if(pageNo > 4) pageNo = 1;

        User communityUser = iUmsUserService.getUserByUsername(userName);
        log.info("文章推荐接口，用户具体信息:{}", communityUser);

        //先查询缓存，如果没有就去做推荐然后插入到缓存表当中
        String object =  (String) redisTemplate.opsForValue().get(POSTS_KEY + communityUser.getId());

        if (object == null || object.isEmpty()) {
            recommendationService.LLMRecommendationPosts(communityUser);
        }

        List<RecommendPostVO> postList = recommendationService.getPostsRecommendations(pageNo, pageSize, communityUser);
        return Result.success(postList);

    }

//    /**
//     * 大模型推荐你可能知道的用户
//     *
//     * @param userName
//     * @return
//     * @throws IOException
//     */
//    @GetMapping("/users/LLMRecommendation")
//    public Result<?> usersFromLLM(@RequestParam(value = USER_NAME) String userName) throws Exception {
//
//        User communityUser = iUmsUserService.getUserByUsername(userName);
//        Object object = redisTemplate.opsForValue().get(USER_KEY + communityUser.getId());
//        if (object != null) {
//            return Result.success();
//        }
//
//
//        log.info("推荐你可能认识的用户：{}", communityUser);
//        //一次放20个用户然后随机数抽5个就可以了
//
//        return Result.success();
//    }

    /**
     * 获取推荐用户接口
     * @param pageNo
     * @param pageSize
     * @param userName
     * @return
     * @throws JsonProcessingException
     */
    @GetMapping("/recommend/users")
    public Result<List<User>> usersRecommendateion(@RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                                   @RequestParam(value = "size", defaultValue = "5") Integer pageSize,
                                                   @RequestHeader(value = USER_NAME , defaultValue = "zmjkk") String userName) throws Exception {


        log.info("用户推荐分页查询：当前页pageNo{},一页多少条消息pageSize{}", pageNo, pageSize);
        if(pageNo > 4) pageNo = 1;
        User communityUser = iUmsUserService.getUserByUsername(userName);
        log.info("用户推荐接口，用户具体信息:{}", communityUser);

        //先查询缓存，如果没有就去做推荐然后插入到缓存表当中
        String object =  (String) redisTemplate.opsForValue().get(USER_KEY + communityUser.getId());
        if (object == null || object.isEmpty()) {
            recommendationService.LLMRecommendationUsers(communityUser);
        }

        List<User> userList = recommendationService.getUsersRecommendations(pageNo, pageSize, communityUser);
        return Result.success(userList);
    }



    @PostMapping("/writePost")
    public Result<String> postWriting(@RequestHeader(value = USER_NAME) String userName,@RequestBody String message) throws IOException {

        User communityUser = iUmsUserService.getUserByUsername(userName);
        log.info("用户具体信息:{},用户输入的信息:{}", communityUser,message);
        String post = recommendationService.writePost(communityUser,message);
        return Result.success(post);

    }


}
