package com.green.service.serviceImpl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.common.exception.ApiAsserts;
import com.green.enumeration.NotifyType;
import com.green.mapper.NotificationMapper;
import com.green.mapper.PostLikesMapper;
import com.green.entity.Notification;
import com.green.entity.Post;
import com.green.entity.PostLikes;
import com.green.entity.User;
import com.green.service.IPostLikesService;
import com.green.service.IPostService;
import com.green.vo.PostLikesVO;
import com.green.service.IUmsUserService;
import com.green.websocket.NotifyWebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class IPostLikesServiceImpl extends ServiceImpl<PostLikesMapper, PostLikes> implements IPostLikesService {

    @Autowired
    private PostLikesMapper postLikesMapper;
    @Autowired
    private IPostService postService;
    @Autowired
    private IUmsUserService userService;
    @Autowired
    private NotificationMapper notificationMapper;

    /**
     * 文章是否点赞
     * @param topicId
     * @param userId
     * @return
     */
    @Override
    @Transactional
    public PostLikesVO togglePostLike(String topicId, String userId) {
        User user = userService.getById(userId);
        Post p = postService.getById(topicId);
        if(p == null){
            ApiAsserts.fail("该文章已被作者或管理员删除");
        }
        // 查询是否点赞过
        PostLikes val = postLikesMapper.select(topicId,userId);
        PostLikesVO vo = new PostLikesVO();
        vo.setTopicId(topicId);
        Integer likes = p.getLikes();
        String authorId = p.getUserId();
        if(val == null) {
            // 未点赞，则点赞
            PostLikes postLikes = PostLikes.builder()
                    .topicId(topicId)
                    .userId(userId)
                    .createTime(LocalDateTime.now())
                    .build();
            postLikesMapper.insert(postLikes);
            vo.setIsLiked(true);
            // 文章点赞总数+1
            p.setLikes(++likes);
            // 推送点赞消息
            JSONObject msg = new JSONObject()
                    .fluentPut("type", NotifyType.LIKE.getValue())
                    .fluentPut("fromUserId", userId)
                    .fluentPut("fromUserAvatar",user.getAvatar())
                    .fluentPut("fromUserName", user.getUsername())
                    .fluentPut("topicId", topicId)
                    .fluentPut("topic", p.getTitle());
            NotifyWebSocketServer.sendNotification(authorId,msg.toJSONString());
            // 插入数据库
            Notification notification = Notification.builder()
                    .createdAt(LocalDateTime.now())
                    .type(NotifyType.LIKE.getCode())
                    .objectId(topicId)
                    .objectType(1)
                    .receiverId(authorId)
                    .senderId(userId)
                    .isRead(0)
                    .build();
            notificationMapper.insert(notification);
        } else {
            // 已点赞，则取消点赞
            vo.setIsLiked(false);
            postLikesMapper.delete(topicId,userId);
            // 文章点赞总数-1
            p.setLikes(--likes);
        }
        postService.updateById(p);
        vo.setLikes(likes);
        return vo;
    }

    /**
     * 验证是否点赞
     * @param userName
     * @param postId
     * @return
     */
    @Override
    public Boolean validate(String userName, String postId) {
        User user = userService.getUserByUsername(userName);
        PostLikes postLikes = this.getOne(new LambdaQueryWrapper<PostLikes>()
                .eq(PostLikes::getTopicId, postId)
                .eq(PostLikes::getUserId,user.getId()));
        return postLikes!=null;
    }
}
