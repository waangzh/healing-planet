package com.green.service.serviceImpl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.common.exception.ApiException;
import com.green.entity.Follow;
import com.green.entity.Notification;
import com.green.enumeration.NotifyType;
import com.green.enumeration.ObjectType;
import com.green.mapper.FollowMapper;
import com.green.entity.User;
import com.green.mapper.UserMapper;
import com.green.service.IFollowService;
import com.green.service.INotificationService;
import com.green.vo.FollowVO;
import com.green.websocket.NotifyWebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service
public class IFollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private INotificationService notificationService;
    @Qualifier("userMapper")
    @Autowired
    private UserMapper userMapper;

    /**
     * 关注用户
     * @param user
     * @param parentId
     */
    @Override
    @Transactional
    public void handleFollow(User user, String parentId) {
        if (parentId.equals(user.getId())) {
            throw new ApiException("您脸皮太厚了，怎么可以关注自己呢 😮");
        }
        Follow one = this.getOne(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getParentId, parentId)
                        .eq(Follow::getFollowerId, user.getId()));
        if (!ObjectUtils.isEmpty(one)) {
            throw new ApiException("已关注");
        }
        // 成功关注
        Follow follow = new Follow();
        follow.setParentId(parentId);
        follow.setFollowerId(user.getId());
        this.save(follow);

        // 推送关注消息给parent_user
        JSONObject msg = new JSONObject()
                .fluentPut("type", NotifyType.FOLLOW.getValue())
                .fluentPut("fromUserId", user.getId())
                .fluentPut("fromUserAvatar",user.getAvatar())
                .fluentPut("fromUserName", user.getUsername());
        NotifyWebSocketServer.sendNotification(parentId,msg.toJSONString());

        // 存入消息表
        Notification notification = Notification.builder()
                .senderId(user.getId())
                .receiverId(parentId)
                .type(NotifyType.FOLLOW.getCode())
                .objectType(ObjectType.USER.getCode())
                .objectId(parentId)
                .createdAt(LocalDateTime.now())
                .build();
        notificationService.save(notification);
    }

    /**
     * 查询用户粉丝列表
     * @param username
     * @return
     */
    @Override
    public List<FollowVO> selectList(User user) {
        List<Follow> list = this.list(new LambdaQueryWrapper<Follow>().eq(Follow::getParentId, user.getId()));
        List<FollowVO> voList = new ArrayList<>();
        for(Follow follow : list) {
            FollowVO vo = new FollowVO();
            User t = userMapper.selectById(follow.getFollowerId());
            vo.setId(t.getId());
            vo.setMessage(t.getMessage());
            vo.setAvatar(t.getAvatar());
            vo.setUsername(t.getUsername());
            voList.add(vo);
        }

        return voList;
    }

    /**
     * 获取我的关注列表
     * @param user
     * @return
     */
    @Override
    public List<FollowVO> selectMyFollowList(User user) {
        List<Follow> list = this.list(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, user.getId()));
        List<FollowVO> voList = new ArrayList<>();
        for(Follow follow : list) {
            FollowVO vo = new FollowVO();
            User t = userMapper.selectById(follow.getParentId());
            vo.setId(t.getId());
            vo.setMessage(t.getMessage());
            vo.setAvatar(t.getAvatar());
            vo.setUsername(t.getUsername());
            voList.add(vo);
        }

        return voList;
    }
}
