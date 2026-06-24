package com.example.demos.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.demos.web.common.result.Result;
import com.example.demos.web.constant.JwtClaimsConstant;
import com.example.demos.web.exception.InvalidKeyException;
import com.example.demos.web.mapper.UserMapper;
import com.example.demos.web.common.properties.JwtProperties;
import com.example.demos.web.pojo.dto.UserDTO;
import com.example.demos.web.pojo.vo.AccountVO;
import com.example.demos.web.pojo.vo.UserVO;
import com.example.demos.web.service.UserService;
import com.example.demos.web.pojo.entity.User;
import com.example.demos.web.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JwtProperties jwtProperties;



    /**
     * 注册
     * @param user
     * @return
     */
    @Override
    public Result<?> register(User user) {
        User existingUser = userMapper.selectOne(Wrappers.lambdaQuery(User.class).eq(User::getUsername, user.getUsername()));
        if (existingUser != null) {
            return Result.error("用户名已重复");
        }
        // 密码进行md5加密
        //user.setPassword(DigestUtils.md5DigestAsHex(user.getPassword().getBytes()));
        // Spring Security 使用 bcrypt
        user.setPassword(new BCryptPasswordEncoder().encode(user.getPassword()));
        userMapper.insert(user);
        return Result.success();
    }

    /**
     * 登录
     * @param user
     * @return
     */
    @Override
    public Result<?> login(UserDTO userDTO){
        // 查询数据库
        User usert = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername,userDTO.getUsername()));
        if(usert == null)
        {
            return Result.error("用户名错误");
        }
        // 输入的密码
        String inputPassword = userDTO.getPassword();
        // 校验
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        boolean isMatch = encoder.matches(inputPassword, usert.getPassword()); // true 或 false
        // 不匹配，密码错误
        if(!isMatch){
            return Result.error("密码错误");
        }
        // 登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, usert.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);
        usert.setToken(token);
        log.info("用户登录:{}",usert);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(usert,userVO);

        return Result.success(userVO);
    }

    /**
     * 新增用户
     * @param user
     * @return
     */
    @Override
    public Result<?> save(User user){
        String username = user.getUsername();
        QueryWrapper<User> queryWrapper = Wrappers.query();
        queryWrapper.eq("user_name",username);
        //
        if(user.getPassword() == null){
            user.setPassword(new BCryptPasswordEncoder().encode("123456"));
        }
        userMapper.insert(user);
        return Result.success();
    }


    /**
     * 根据id更新用户信息
     * @param user
     * @return
     */
    @Override
    public  Result<?> update(User user){
        userMapper.updateById(user);
        return Result.success();
    }

    /**
     * 批量删除用户
     * @param ids
     * @return
     */
    @Override
    public  Result<?> deleteBatch(List<Integer> ids){
        userMapper.deleteBatchIds(ids);
        return Result.success();
    }


    /**
     * 查询所有用户
     * @return
     */
    public Result<?> search(){
        List<User> users = userMapper.selectList(null);
        return Result.success(users);
    }
    public Result<?> selectById(Integer id){
        return Result.success(userMapper.selectById(id));
    }

    /**
     * 更新密码
     * @param id
     * @param password
     * @return
     */
    @Override
    public  Result<?> password(Integer id, String password){
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id",id);
        User user = new User();
        user.setPassword(new BCryptPasswordEncoder().encode(password));
        userMapper.update(user,updateWrapper);
        return Result.success();
    }




    /**
     * 根据key值修改密码
     * @param key
     * @param changePasswrod
     * @return
     */
    @Override
    public AccountVO changePassword(String key, String changePasswrod) {
        Integer id =  userMapper.existsUser(key);
        if(id != null){
            UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id",id);
            User user = new User();
            user.setPassword(new BCryptPasswordEncoder().encode(changePasswrod));
            userMapper.update(user,updateWrapper);
            String account = userMapper.getAccount(id);
            AccountVO accountVO = AccountVO.builder()
                    .account(account)
                    .password(changePasswrod)
                    .build();
            return accountVO;
        }else
            throw new InvalidKeyException("输入的key值正确");
    }

}
