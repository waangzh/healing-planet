package com.green.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.green.entity.Notification;
import com.green.entity.User;
import com.green.vo.NotificationVO;
import java.util.List;

public interface INotificationService extends IService<Notification> {
    /**
     * 获取消息通知
     * @param u
     * @return
     */
    List<NotificationVO> getNotification(User u);
}
