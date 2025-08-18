package com.green.controller.admin;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.common.api.Result;
import com.green.common.exception.ApiAsserts;
import com.green.dto.LoginDTO;
import com.green.dto.RegisterDTO;
import com.green.dto.UserDTO;
import com.green.entity.User;
import com.green.entity.UserQuery;
import com.green.service.CaptchaService;
import com.green.service.IUmsUserService;
import com.green.vo.LoginVO;
import com.green.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.green.security.jwt.JwtUtil.USER_NAME;

@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private IUmsUserService iUserService;

    /**
     * 管理员登录
     * @param dto
     * @return
     */
    @PostMapping("/login")
    public Result<?> adminLogin(@RequestBody LoginDTO dto){
        log.info("管理员登录:{}",dto);
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
     * 分页查询所有用户
     * @param tab
     * @param pageNo
     * @param pageSize
     * @return
     */
    @PostMapping("/list")
    public Result<Page<UserVO>> list(@RequestBody UserQuery userQuery) {
        log.info("分页查询所有用户:{}",userQuery);
        Page<UserVO> list = iUserService.getList(userQuery);
        log.info("查询结果:{}",list);
        return Result.success(list);
    }


    /**
     * 批量删除用户
     * @param ids
     * @return
     */
    @DeleteMapping("/delete")
    public Result<?> remove(@RequestParam List<String> ids) {
         log.info("批量删除用户:{}",ids);
         iUserService.removeByIds(ids);
         return Result.success();
    }

    /**
     * 更新用户信息
     * @param userName
     * @param dto
     * @return
     */
    @PutMapping("/update")
    public Result<?> update(@RequestBody UserDTO dto) {
        log.info("更新用户信息:{}",dto);
        iUserService.update(dto.getUsername(),dto);
        return Result.success();
    }

    /**
     * 新增用户
     * @param user
     * @return
     */
    @PostMapping("/add")
    public Result<?> add(@RequestBody RegisterDTO dto){
        log.info("新增用户:{}",dto.getUsername());
        User user = iUserService.executeRegister(dto);
        if (ObjectUtils.isEmpty(user)) {
            return Result.failed("添加失败");
        }
        Map<String, Object> map = new HashMap<>(16);
        map.put("user", user);
        return Result.success(map);
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

}
