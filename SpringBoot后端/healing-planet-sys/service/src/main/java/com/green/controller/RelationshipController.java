package com.green.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.green.common.api.Result;
import com.green.common.exception.ApiAsserts;
import com.green.entity.Follow;
import com.green.entity.User;
import com.green.service.IFollowService;
import com.green.service.IUmsUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.green.jwt.JwtUtil.USER_NAME;

@RestController
@RequestMapping("/relationship")
@Slf4j
public class RelationshipController extends BaseController {

    @Resource
    private IFollowService iFollowService;

    @Resource
    private IUmsUserService iUserService;

    /**
     * 根据用户id关注用户
     * @param userName
     * @param parentId
     * @return
     */
    @GetMapping("/subscribe/{userId}")
    public Result<Object> handleFollow(@RequestHeader(value = USER_NAME) String userName, @PathVariable("userId") String parentId) {
        log.info("{}关注{}", userName, parentId);
        User umsUser = iUserService.getUserByUsername(userName);

        iFollowService.handleFollow(umsUser,parentId);

        return Result.success(null, "关注成功");
    }

    /**
     * 根据用户id取消关注用户
     * @param userName
     * @param parentId
     * @return
     */
    @GetMapping("/unsubscribe/{userId}")
    public Result<Object> handleUnFollow(@RequestHeader(value = USER_NAME) String userName
            , @PathVariable("userId") String parentId) {
        User umsUser = iUserService.getUserByUsername(userName);
        Follow one = iFollowService.getOne(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getParentId, parentId)
                        .eq(Follow::getFollowerId, umsUser.getId()));
        if (ObjectUtils.isEmpty(one)) {
            ApiAsserts.fail("未关注！");
        }
        iFollowService.remove(new LambdaQueryWrapper<Follow>().eq(Follow::getParentId, parentId)
                .eq(Follow::getFollowerId, umsUser.getId()));
        return Result.success(null, "取关成功");
    }

    /**
     * 验证是否关注
     * @param userName
     * @param topicUserId 当前浏览话题作者ID
     * @return
     */
    @GetMapping("/validate/{topicUserId}")
    public Result<Map<String, Object>> isFollow(@RequestHeader(value = USER_NAME) String userName
            , @PathVariable("topicUserId") String topicUserId) {
        User umsUser = iUserService.getUserByUsername(userName);
        Map<String, Object> map = new HashMap<>(16);
        map.put("hasFollow", false);
        if (!ObjectUtils.isEmpty(umsUser)) {
            Follow one = iFollowService.getOne(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getParentId, topicUserId)
                    .eq(Follow::getFollowerId, umsUser.getId()));
            if (!ObjectUtils.isEmpty(one)) {
                map.put("hasFollow", true);
            }
        }
        return Result.success(map);
    }

    /**
     * 根据用户名获取粉丝列表
     *
     * @param username
     * @return
     */
    @GetMapping("/fans")
    public Result<List<Follow>> getFollowList(@RequestParam("username") String username){
        log.info("获取我的粉丝:{}",username);
        User user = iUserService.getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (ObjectUtils.isEmpty(user)) {
            ApiAsserts.fail("用户不存在");
        }
        List<Follow> list = iFollowService.list(new LambdaQueryWrapper<Follow>().eq(Follow::getParentId, user.getId()));
        return Result.success(list);
    }

    /**
     * 根据用户名获取关注列表
     *
     * @param username
     * @return
     */
    @GetMapping("/followers")
    public Result<List<Follow>> getMyFollowList(@RequestParam("username") String username){
        log.info("获取我的关注:{}",username);
        User user = iUserService.getUserByUsername(username);
        if (ObjectUtils.isEmpty(user)) {
            ApiAsserts.fail("用户不存在");
        }
        List<Follow> list = iFollowService.list(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, user.getId()));
        return Result.success(list);
    }
}
