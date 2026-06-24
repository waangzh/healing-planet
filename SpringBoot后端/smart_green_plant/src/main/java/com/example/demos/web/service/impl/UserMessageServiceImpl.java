package com.example.demos.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demos.web.mapper.UserMessageMapper;
import com.example.demos.web.pojo.entity.UserMessage;
import com.example.demos.web.service.UserMessageService;
import com.example.demos.web.service.WebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service
public class UserMessageServiceImpl implements UserMessageService {

    @Autowired
    private UserMessageMapper userMessageMapper;
    @Autowired
    private WebSocketService webSocketService;

    /**
     * 创建站内警告消息
     * @param userId
     * @param title
     * @param content
     */
    public void createWarningMessage(Long userId, String title, String content) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setTitle(title);
        message.setContent(content);
        message.setType("WARNING");
        message.setIsRead(false);
        message.setCreatedTime(LocalDateTime.now());
        userMessageMapper.insert(message);

        //推送消息到前端
        webSocketService.sendMessageToUser(userId, "您有一条新消息：" + title);
    }

    /**
     * 获取用户消息
     * @param userId
     * @return
     */
    @Override
    public List<UserMessage> getUserMessages(Long userId) {
        return userMessageMapper.selectList(
                new LambdaQueryWrapper<UserMessage>()
                        .eq(UserMessage::getUserId, userId)
                        .orderByDesc(UserMessage::getCreatedTime)
        );

    }

    /**
     * 获取用户未读消息数量
     * @param userId
     * @return
     */
    public int getUnreadCount(Long userId) {
        return userMessageMapper.selectCount(
                new LambdaQueryWrapper<UserMessage>()
                        .eq(UserMessage::getUserId, userId)
                        .eq(UserMessage::getIsRead, false)
        );
    }

    /**
     * 消息标为已读
     * @param messageId
     */
    @Override
    public void markMessageRead(Long messageId) {
        UserMessage userMessage = userMessageMapper.selectById(messageId);

        //标记为已读
        userMessage.setIsRead(true);
        userMessageMapper.updateById(userMessage);
    }

    /**
     * 删除消息
     * @param messageId
     */
    @Override
    public void deleteMessage(Long messageId) {
        userMessageMapper.deleteById(messageId);
    }
}
