package com.example.demos.web.controller;

import com.example.demos.web.common.result.Result;
import com.example.demos.web.context.BaseContext;
import com.example.demos.web.pojo.entity.UserMessage;
import com.example.demos.web.service.UserMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/messages")
@Slf4j
public class UserMessageController {

    @Autowired
    private UserMessageService userMessageService;

    /**
     * 获取用户消息列表
     * @return
     */
    @GetMapping
    public Result<List<UserMessage>> getMessages() {
        Long userId = BaseContext.getCurrentId();
        List<UserMessage> list = userMessageService.getUserMessages(userId);
        return Result.success(list);
    }


    /**
     * 获取用户未读消息数量
     * @return
     */
    @GetMapping("/unreadNum")
    public Result<?> getUnreadCount() {
        Long userId = BaseContext.getCurrentId();
        int count = userMessageService.getUnreadCount(userId);

        return Result.success(count);
    }
    /**
     * 消息标记为已读
     * @param messageId
     * @return
     */
    @PutMapping("/{messageId}/read")
    public Result<?> markAsRead(@PathVariable Long messageId) {
        userMessageService.markMessageRead(messageId);
        return Result.success();
    }

    /**
     * 删除消息
     * @param messageId
     * @return
     */
    @DeleteMapping("/delete/{messageId}")
    public Result<?> deleteMessage(@PathVariable Long messageId) {
        log.info("删除消息:{}", messageId);
        userMessageService.deleteMessage(messageId);
        return Result.success();
    }

}
