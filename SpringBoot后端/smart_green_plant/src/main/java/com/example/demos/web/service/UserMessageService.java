package com.example.demos.web.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demos.web.pojo.entity.UserMessage;

import java.time.LocalDateTime;
import java.util.List;

public interface UserMessageService {


    /**
     * 创建站内消息
     * @param userId
     * @param title
     * @param content
     */
    void createWarningMessage(Long userId, String title, String content);


    List<UserMessage> getUserMessages(Long userId);
    /**
     * 获取用户未读消息数量
     * @param userId
     * @return
     */
    int getUnreadCount(Long userId);

    /**
     * 消息标为已读
     * @param messageId
     */
    void markMessageRead(Long messageId);

    /**
     * 删除消息
     * @param messageId
     */
    void deleteMessage(Long messageId);


    //TODO 定时清理消息
}
