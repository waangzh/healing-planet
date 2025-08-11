package com.green.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.green.common.api.Result;
import com.green.entity.Notification;
import com.green.entity.User;
import com.green.service.INotificationService;
import com.green.service.IUmsUserService;
import com.green.vo.NotificationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.green.security.jwt.JwtUtil.USER_NAME;

/**
 * 消息通知相关接口
 */
@RestController
@RequestMapping("/notification")
@Slf4j
public class NotificationController {
    @Autowired
    private IUmsUserService userService;
    @Autowired
    private INotificationService notificationService;


    /**
     * 获取消息通知
     * @param userName
     * @return
     */
    @GetMapping
    public Result<List<NotificationVO>> getNotification(@RequestHeader(value = USER_NAME) String userName) {
        log.info("获取消息通知:{}",userName);
        User u = userService.getUserByUsername(userName);
        List<NotificationVO> voList = notificationService.getNotification(u);
        return Result.success(voList);
    }

    /**
     * 标记当前消息为已读
     * @param messageId
     * @return
     */
    @PutMapping("/{messageId}")
    public Result<Object> markAsRead(@PathVariable String messageId) {
        log.info("标记消息为已读:{}",messageId);
        Notification notification = notificationService.getById(messageId);
        notification.setIsRead(1);
        notificationService.updateById(notification);

        return Result.success();
    }


    /**
     * 获取用户未读消息数量
     * @param userName
     * @return
     */
    @GetMapping("/getUnreadCount")
    public Result<Integer> getUnreadCount(@RequestHeader(value = USER_NAME) String userName) {
        log.info("获取用户未读消息数量:{}",userName);
        User user = userService.getUserByUsername(userName);
        Integer count = notificationService.count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId,user.getId())
                .eq(Notification::getIsRead,0));

        return Result.success(count);
    }


    @DeleteMapping("/{messageId}")
    public Result<Object> deleteNotification(@PathVariable String messageId) {
        log.info("删除消息:{}",messageId);
        notificationService.removeById(messageId);
        return Result.success();
    }
}
