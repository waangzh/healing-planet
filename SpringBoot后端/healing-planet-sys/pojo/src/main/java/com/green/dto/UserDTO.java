package com.green.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserDTO {

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户昵称
     */
    private String alias;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机
     */
    private String mobile;

    /**
     * 简介
     */
    private String bio;


    /**
     * 修改时间
     */
    private Date modifyTime;

    /**
     * 个人主页留言
     */
    private String message;
}
