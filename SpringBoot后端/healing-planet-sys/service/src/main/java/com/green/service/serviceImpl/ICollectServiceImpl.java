package com.green.service.serviceImpl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.enumeration.NotifyType;
import com.green.enumeration.ObjectType;
import com.green.mapper.CollectMapper;
import com.green.mapper.NotificationMapper;
import com.green.mapper.TopicMapper;
import com.green.dto.CollectDTO;
import com.green.entity.Collect;
import com.green.entity.Notification;
import com.green.entity.Post;
import com.green.entity.User;
import com.green.service.IPostService;
import com.green.vo.CollectVO;
import com.green.service.ICollectService;
import com.green.service.IUmsUserService;
import com.green.websocket.NotifyWebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

@Service
public class ICollectServiceImpl extends ServiceImpl<CollectMapper, Collect> implements ICollectService {
    @Autowired
    private IUmsUserService userService;
    @Autowired
    private CollectMapper collectMapper;
    @Autowired
    private IPostService postService;
    @Qualifier("topicMapper")
    @Autowired
    private TopicMapper topicMapper;
    @Autowired
    private NotificationMapper notificationMapper;


    /**
     * 获取用户收藏列表
     * @param currentId
     * @return
     */
    @Override
    public Page<CollectVO> list(Page<CollectVO> page, String currentId) {
        Collect collect = Collect.builder()
                .userId(currentId)
                .build();
        // 获取收藏列表
        List<String> collectList = collectMapper.selectByUserId(currentId);
        Page<CollectVO> voPage = topicMapper.selectByCollectId(page,collectList);
        return voPage;
    }

    /**
     * （取消）收藏文章
     * @param collectDTO
     * @return
     */
    @Override
    @Transactional
    public HashMap<String, Object> isCollected(CollectDTO collectDTO) {
        User user = userService.getUserByUsername(collectDTO.getUserName());
        String userId = user.getId();
        String topicId = collectDTO.getTopicId();
        Post p = postService.getById(collectDTO.getTopicId());
        String authorId = p.getUserId();
        Collect collect = Collect.builder()
                .userId(userId)
                .topicId(topicId)
                .createTime(LocalDateTime.now())
                .build();
        Collect one = collectMapper.selectOne(new LambdaQueryWrapper<Collect>()
                .eq(Collect::getUserId, userId)
                .eq(Collect::getTopicId, collectDTO.getTopicId()));
        HashMap<String, Object> map = new HashMap<>();
        if(one == null) { // 未收藏
            this.save(collect);
            // 收藏数++
            p.setCollects(p.getCollects() + 1);
            postService.updateById(p);
            map.put("collect",p.getCollects());
            // 推送关注消息
            JSONObject msg = new JSONObject()
                    .fluentPut("type", NotifyType.COLLECT.getValue())
                    .fluentPut("fromUserId", userId)
                    .fluentPut("fromUserAvatar",user.getAvatar())
                    .fluentPut("fromUserName", user.getUsername())
                    .fluentPut("topicId", topicId)
                    .fluentPut("topic", p.getTitle());
            NotifyWebSocketServer.sendNotification(authorId,msg.toJSONString());
            // 插入数据库
            // 插入数据库
            Notification notification = Notification.builder()
                    .createdAt(LocalDateTime.now())
                    .type(NotifyType.COLLECT.getCode())
                    .objectId(topicId)
                    .objectType(ObjectType.POST.getCode())
                    .receiverId(authorId)
                    .senderId(userId)
                    .isRead(0)
                    .build();
            notificationMapper.insert(notification);
            map.put("isCollected",true);
        } else { // 已收藏
            // 取消收藏
            collectMapper.deleteCollect(collect);
            // 收藏数--
            p.setCollects(p.getCollects() - 1);
            postService.updateById(p);
            map.put("collect",p.getCollects());
            map.put("isCollected",false);
        }
        return map;
    }

    /**
     * 验证是否收藏
     * @param userName
     * @param postId
     * @return
     */
    @Override
    public Boolean validate(String userName, String postId) {
        User user = userService.getUserByUsername(userName);
        Collect collect = this.getOne(new LambdaQueryWrapper<Collect>()
                .eq(Collect::getTopicId,postId)
                .eq(Collect::getUserId,user.getId()));
        return collect != null;
    }
}
