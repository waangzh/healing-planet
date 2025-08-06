package com.green.service.serviceImpl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.common.exception.ApiAsserts;
import com.green.enumeration.NotifyType;
import com.green.enumeration.ObjectType;
import com.green.mapper.CommentMapper;
import com.green.mapper.NotificationMapper;
import com.green.mapper.TopicMapper;
import com.green.mapper.UserMapper;
import com.green.dto.CommentDTO;
import com.green.entity.Comment;
import com.green.entity.Notification;
import com.green.entity.Post;
import com.green.entity.User;
import com.green.vo.CommentVO;
import com.green.service.ICommentService;
import com.green.websocket.NotifyWebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;


@Slf4j
@Service
public class ICommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements ICommentService {
    @Autowired
    private TopicMapper topicMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private NotificationMapper notificationMapper;

    public ICommentServiceImpl(@Qualifier("topicMapper") TopicMapper topicMapper) {
        this.topicMapper = topicMapper;
    }

    /**
     * 根据文章id获取评论
     * @param topicId
     * @return
     */
    @Override
    public List<CommentVO> getCommentsByTopicID(String topicId) {

        // 查询所有一级评论
        List<CommentVO> rootComments = this.baseMapper.selectRootComments(topicId);

        // 查询一级评论对应的二级评论
        rootComments.forEach(root -> {
            List<CommentVO> children = baseMapper.selectByRootId(root.getId());
            root.setChildren(children);
        });


        return rootComments;
    }

    /**
     *  添加评论
     * @param dto
     * @param user
     * @return
     */
    @Override
    @Transactional
    public CommentVO create(CommentDTO dto, User user) {
        // 添加评论
        Comment comment = Comment.builder()
                .userId(user.getId())
                .content(dto.getContent())
                .topicId(dto.getTopic_id())
                .parentId(dto.getParentId())
                .createTime(new Date())
                .build();
        if(dto.getContent().length() > 1000){
            ApiAsserts.fail("评论长度已超过最大限制！请重新输入！");
        }

        // 处理多级评论
        if(dto.getParentId() != null){
            // this调用IService的接口方法
            Comment parent = this.getById(dto.getParentId());
            if(parent == null){
                ApiAsserts.fail( "父评论不存在或已被删除");
            }
            comment.setParentId(parent.getId());
            comment.setLevel(2); // 二级评论

            // 设置root_id：如果父评论是二级评论，则继承其root_id
            comment.setRootId(parent.getRootId() != null ? parent.getRootId() : parent.getId());

            comment.setReplyToUserId(dto.getReplyToUserId());
        } else {
            comment.setLevel(1); // 一级评论
            // 一级评论的parent_id设置为空
            // 一级评论设置root_id为自己（插入后更新）
            comment.setRootId(comment.getId());
        }

        this.baseMapper.insert(comment);
        if(comment.getLevel() == 1){
            comment.setRootId(comment.getId());
            this.baseMapper.updateById(comment);
        }

        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(comment, vo);
        vo.setUserAvatar(user.getAvatar());//评论人头像
        vo.setAuthorName(dto.getUserName());// 评论作者
        // 被回复的用户
        User replyUser = userMapper.selectById(dto.getReplyToUserId());
        if(replyUser != null) {
            vo.setReplyToUsername(replyUser.getUsername());
        }
        // 评论总数++
        String topicId = dto.getTopic_id();
        Post post = topicMapper.selectById(topicId);
        post.setComments(post.getComments() + 1);
        topicMapper.updateById(post);


        // 推送评论消息
        JSONObject msg = new JSONObject()
                .fluentPut("type", NotifyType.COMMENT.getValue())
                .fluentPut("fromUserId", vo.getUserId())
                .fluentPut("fromUserAvatar",vo.getUserAvatar())
                .fluentPut("fromUserName", vo.getAuthorName())
                .fluentPut("fromUserAvatar",vo.getUserAvatar())
                .fluentPut("topicId", topicId)
                .fluentPut("topic", post.getTitle());
        Notification notification = Notification.builder()
                .senderId(vo.getUserId())
                .objectType(ObjectType.COMMENT.getCode())
                .type(NotifyType.COMMENT.getCode())
                .objectId(topicId)
                .isRead(0)
                .createdAt(LocalDateTime.now())
                .build();
        if(vo.getReplyToUserId()!=null)
        {
            NotifyWebSocketServer.sendNotification(vo.getReplyToUserId(),msg.toJSONString());
            notification.setReceiverId(vo.getReplyToUserId());

        }
        NotifyWebSocketServer.sendNotification(post.getUserId(),msg.toJSONString());
        notification.setReceiverId(post.getUserId());
        // 插入消息表
        notificationMapper.insert(notification);
        return vo;
    }
}
