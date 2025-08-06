package com.green.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.green.entity.Follow;
import com.green.entity.User;


public interface IFollowService extends IService<Follow> {
    /**
     * 关注用户
     * @param umsUser
     * @param parentId
     */
    void handleFollow(User user, String parentId);
}
