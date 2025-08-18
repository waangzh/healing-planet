package com.green.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.entity.User;
import com.green.enumeration.NotifyType;
import com.green.enumeration.ObjectType;
import com.green.mapper.NotificationMapper;
import com.green.mapper.PostMapper;
import com.green.mapper.UserMapper;
import com.green.entity.Notification;
import com.green.service.INotificationService;
import com.green.vo.NotificationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class INotificationImpl extends ServiceImpl<NotificationMapper, Notification> implements INotificationService {

    @Autowired
    private UserMapper userMapper;
    @Qualifier("postMapper")
    @Autowired
    private PostMapper postMapper;

    /**
     * 获取消息通知
     * @param u
     * @return
     */
    @Override
    public List<NotificationVO> getNotification(User u) {
        List<Notification> list = this.list(new
                LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId,u.getId())
                .orderByDesc(Notification::getCreatedAt));

        List<NotificationVO> vos = new ArrayList<>();
        for (Notification notification : list) {
            NotificationVO notificationVO = new NotificationVO();
            BeanUtils.copyProperties(notification,notificationVO);
            // 获取发送用户信息
            User user = userMapper.selectById(notification.getSenderId());
            notificationVO.setSenderName(user.getUsername());
            notificationVO.setSenderAvatar(user.getAvatar());
            // 设置消息类型
            notificationVO.setType(NotifyType.getByCode(notification.getType()));
            notificationVO.setObjectType(ObjectType.getValue(notification.getObjectType()));
            if(notification.getObjectType()!=3){
                String title = postMapper.selectById(notificationVO.getObjectId()).getTitle();
                notificationVO.setObjectName(title);
            } else {
                notificationVO.setObjectName("");
            }
            // 是否阅读
            notificationVO.setIsRead(notification.getIsRead() == 1);
            vos.add(notificationVO);
        }

        return vos;
    }
}
