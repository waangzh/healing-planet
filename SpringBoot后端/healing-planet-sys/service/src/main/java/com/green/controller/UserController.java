package com.green.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.common.api.Result;
import com.green.common.exception.ApiAsserts;
import com.green.dto.LoginDTO;
import com.green.dto.RegisterDTO;
import com.green.dto.UserDTO;
import com.green.entity.Post;
import com.green.entity.User;
import com.green.service.CaptchaService;
import com.green.vo.LoginVO;
import com.green.vo.UserVO;
import com.green.service.IPostService;
import com.green.service.IUmsUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

import static com.green.security.jwt.JwtUtil.USER_NAME;


@RestController
@RequestMapping("/ums/user")
@Slf4j
public class UserController extends BaseController {
    @Resource
    private IUmsUserService iUserService;
    @Resource
    private IPostService iPostService;
    @Resource
    private CaptchaService captchaService;

    /**
     * 用户注册
     * @param dto
     * @return
     */
    @PostMapping( "/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto) {
        log.info("用户注册:{}",dto.getUsername());
        User user = iUserService.executeRegister(dto);
        if (ObjectUtils.isEmpty(user)) {
            return Result.failed("账号注册失败");
        }
        Map<String, Object> map = new HashMap<>(16);
        map.put("user", user);
        return Result.success(map);
    }

    /**
     * 用户登录
     * @param dto
     * @return
     */
    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginDTO dto) {
        log.info("用户登录:{}",dto.getUsername());
        User user = iUserService.getUserByUsername(dto.getUsername());
        if(!user.getStatus()){
            ApiAsserts.fail("账号已停用!");
        }
        // 开启验证码功能
        boolean needAuthCode = false;
        if (needAuthCode) {
            String msg = captchaService.checkImageCode(dto.getNonceStr(),dto.getValue());
            if (StringUtils.isNotBlank(msg)) {
                ApiAsserts.fail(msg);
            }
        }
        LoginVO vo = iUserService.executeLogin(dto);
        if (ObjectUtils.isEmpty(vo.getToken())) {
            return Result.failed("账号密码错误");
        }

        return Result.success(vo, "登录成功");
    }

    /**
     * 获取用户信息
     * @param userName
     * @return
     */
    @GetMapping("/info")
    @Cacheable(cacheNames = "userInfo",key = "#userName")
    public Result<UserVO> getUser(@RequestParam String userName) {
        log.info("获取用户信息:{}",userName);
        User user = iUserService.getUserByUsername(userName);
        UserVO vo = iUserService.getInfoDetail(user);
        return Result.success(vo);
    }

    /**
     * 登出
     * @return
     */
    @GetMapping("/logout")
    @CacheEvict(cacheNames = "userInfo",key = "#userName")
    public Result<Object> logOut(@RequestParam String userName) {
        log.info("用户登出:{}",userName);
        return Result.success(null, "注销成功");
    }

    /**
     *
     * 根据用户名查询文章
     * @param username
     * @param pageNo
     * @param size
     * @return
     */
    @GetMapping("/{username}")
    public Result<Map<String, Object>> getUserByName(@PathVariable("username") String username,
                                                     @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                                     @RequestParam(value = "size", defaultValue = "10") Integer size) {
        Map<String, Object> map = new HashMap<>(16);
        User user = iUserService.getUserByUsername(username);
        Assert.notNull(user, "用户不存在");
        Page<Post> page = iPostService.page(new Page<>(pageNo, size),
                new LambdaQueryWrapper<Post>().eq(Post::getUserId, user.getId())
                        .orderByDesc(Post::getCreateTime));
        map.put("user", user);
        map.put("topics", page);
        return Result.success(map);
    }

    /**
     * 更新用户信息
     * @param userName
     * @param userDTO
     * @return
     */
    @PostMapping("/update")
    @CacheEvict(cacheNames = "userInfo",key = "#userName")
    public Result<User> updateUser(@RequestHeader(value = USER_NAME)String userName,@RequestBody UserDTO userDTO) {
        log.info("更新用户信息:{}",userName);
        iUserService.update(userName,userDTO);
        return Result.success();
    }
}
