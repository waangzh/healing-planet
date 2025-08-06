package com.green.service;

import com.baomidou.mybatisplus.extension.service.IService;


import com.green.entity.User;
import com.green.dto.LoginDTO;
import com.green.dto.RegisterDTO;
import com.green.dto.UserDTO;
import com.green.vo.ProfileVO;
import com.green.vo.UserVO;


public interface IUmsUserService extends IService<User> {

    /**
     * 注册功能
     *
     * @param dto
     * @return 注册对象
     */
    User executeRegister(RegisterDTO dto);
    /**
     * 获取用户信息
     *
     * @param username
     * @return dbUser
     */
    User getUserByUsername(String username);
    /**
     * 用户登录
     *
     * @param dto
     * @return 生成的JWT的token
     */
    String executeLogin(LoginDTO dto);
    /**
     * 获取用户信息
     *
     * @param id 用户ID
     * @return
     */
    ProfileVO getUserProfile(String id);

    /**
     * 获取用户详细信息
     * @param user
     * @return
     */
    UserVO getInfoDetail(User user);

    /**
     * 更新用户信息
     * @param userDTO
     * @param username
     */
    void update(String username, UserDTO userDTO);
}
