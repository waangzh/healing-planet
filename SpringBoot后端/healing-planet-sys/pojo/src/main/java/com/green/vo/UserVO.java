package com.green.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO implements Serializable {

    /**
     * 用户id
     */
    private String id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户昵称
     */
    private String alias;


    private String avatar;

    private String email;

    /**
     * 手机
     */
    private String mobile;

    private String bio;

    /**
     * 积分
     */
    private Integer score;

    /**
     * 是否激活
     */
    private Boolean active;

    /**
     * 状态。1:使用，0:已停用
     */
    private Boolean status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 修改时间
     */
    private Date modifyTime;

    /**
     * 个人主页留言
     */
    private String message;


    /**
     * 用户发布文章数
     */
    private Integer postCount;

    /**
     * 关注者数
     */
    private Integer followerCount;


    /**
     * 关注数
     */
    private Integer followingCount;

    /**
     * 是否购买设备
     */
    private Boolean isPurchased = false;
}
