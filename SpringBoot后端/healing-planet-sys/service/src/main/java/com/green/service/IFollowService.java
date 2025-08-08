package com.green.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.green.entity.Follow;
import com.green.entity.User;
import com.green.vo.FollowVO;

import java.util.List;


public interface IFollowService extends IService<Follow> {
    /**
     * 关注用户
     * @param umsUser
     * @param parentId
     */
    void handleFollow(User user, String parentId);

    /**
     * 查询用户粉丝列表
     * @param username
     * @return
     */
    List<FollowVO> selectList(User user);
}
