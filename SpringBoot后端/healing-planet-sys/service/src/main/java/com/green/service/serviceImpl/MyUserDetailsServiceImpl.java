package com.green.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.green.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MyUserDetailsServiceImpl implements UserDetailsService{

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 从数据库查找用户
        com.green.entity.User user = userMapper.selectOne(
                new LambdaQueryWrapper<com.green.entity.User>()
                        .eq(com.green.entity.User::getUsername, username));
        log.info("当前用户:{}",user);

        if(user.getRoleId()==1){
            return User.withUsername(user.getUsername())
                    .password(user.getPassword()) // 加密后的密码
                    .roles("ADMIN") // 自动加 ROLE_ 前缀
                    .build();
        } else if(user.getRoleId()==0){
            return User.withUsername(user.getUsername())
                    .password(user.getPassword()) // 加密后的密码
                    .roles("USER") // 自动加 ROLE_ 前缀
                    .build();
        }
        throw new UsernameNotFoundException("用户不存在");
    }
}
