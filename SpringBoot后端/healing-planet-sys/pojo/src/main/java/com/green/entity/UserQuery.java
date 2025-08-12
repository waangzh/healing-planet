package com.green.entity;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserQuery {

    /**
     * 用户名
     */
    private String username;


    /**
     * 昵称
     */
    private String alias;

    /**
     * 状态 1:使用，0:已停用
     */
    private Boolean status;


    /**
     * 用户发布文章数
     */
    private Integer postCount;

    /**
     * 粉丝数量
     */
    private Integer followerCount;


    /**
     * 关注数量
     */
    private Integer followingCount;

    /**
     * 起始页
     */
    private Integer pageNo;

    /**
     * 页大小
     */
    private Integer pageSize;

}
