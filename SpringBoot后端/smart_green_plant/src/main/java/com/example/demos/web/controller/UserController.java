package com.example.demos.web.controller;


import com.example.demos.web.common.result.Result;
import com.example.demos.web.exception.CustomException;
import com.example.demos.web.pojo.dto.UserDTO;
import com.example.demos.web.pojo.entity.User;
import com.example.demos.web.pojo.vo.AccountVO;
import com.example.demos.web.service.CaptchaService;
import com.example.demos.web.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private CaptchaService captchaService;

    /**
     * 登录
     * @param user
     * @return
     */
    @PostMapping("/login")
    public Result<?> login(@RequestBody UserDTO dto){
        log.info("用户登录：{}",dto);
        // 开启验证码功能
        boolean needAuthCode = false;
        if (needAuthCode) {
            String msg = captchaService.checkImageCode(dto.getNonceStr(),dto.getValue());
            if (StringUtils.isNotBlank(msg)) {
                throw new CustomException("-1",msg);
            }
        }
        return userService.login(dto);
    }

    /**
     * 注册
     * @param user
     * @return
     */
    @PostMapping("/register")
    public Result<?> register(@RequestBody User user){
        return userService.register(user);
    }

    /**
     * 新增用户
     * @param user
     * @return
     */
    @PostMapping
    public Result<?> add(@RequestBody User user){
        return userService.save(user);
    }

    /**
     * 修改密码
     * @param user
     * @return
     */
    @PutMapping
    public  Result<?> password(@RequestBody User user){
        String password = user.getPassword();
        Integer id = user.getId();
        log.info("新密码:{}",password);
        return userService.password(id,password);
    }

    @PostMapping("/key")
    public Result<AccountVO> changePassword(String key,String changePasswrod){
        log.info("key：{}",key);
        AccountVO accountVO = userService.changePassword(key, changePasswrod);
        return Result.success(accountVO);
    }

    /**
     * 批量删除用户
     * @param ids
     * @return
     */
    @PostMapping("/deleteBatch")
    public  Result<?> deleteBatch(@RequestBody List<Integer> ids){
        return userService.deleteBatch(ids);
    }


    /**
     * 查询所有用户
     * @return
     */
    @GetMapping
    public Result<?> search(){
        return userService.search();
    }

    /**
     * 根据id查询用户
     * @param id
     * @return
     */
    @GetMapping("{id}")
    public Result<?> selectById(@PathVariable Integer id){
        return userService.selectById(id);
    }

    /**
     * 更新用户信息
     * @param user
     * @return
     */
    @PutMapping("/update")
    public  Result<?> update(@RequestBody User user){
        return userService.update(user);
    }

}
