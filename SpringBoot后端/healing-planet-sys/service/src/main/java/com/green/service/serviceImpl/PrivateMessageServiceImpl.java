package com.green.service.serviceImpl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.common.exception.ApiException;
import com.green.dto.PrivateMessageSendDTO;
import com.green.entity.PrivateMessage;
import com.green.entity.User;
import com.green.mapper.PrivateMessageMapper;
import com.green.mapper.UserMapper;
import com.green.service.IPrivateMessageService;
import com.green.service.IUmsUserService;
import com.green.vo.PrivateChatSessionVO;
import com.green.vo.PrivateMessageVO;
import com.green.websocket.ChatWebSocketServer;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PrivateMessageServiceImpl implements IPrivateMessageService {

    @Autowired
    private PrivateMessageMapper privateMessageMapper;

    @Autowired
    private IUmsUserService userService;

    @Autowired
    private UserMapper userMapper;

    @Override
    public PrivateMessageVO sendByHttp(String senderUserName, PrivateMessageSendDTO dto) {
        User sender = userService.getUserByUsername(senderUserName);
        return saveAndDispatch(sender.getId(), dto.getToUserId(), dto.getContent());
    }

    @Override
    public void handleWebSocketSend(String fromUserId, String toUserId, String content) {
        saveAndDispatch(fromUserId, toUserId, content);
    }

    @Override
    public Page<PrivateMessageVO> getConversation(String userName, String peerUserId, Integer pageNo, Integer pageSize) {
        User self = userService.getUserByUsername(userName);
        User peer = userMapper.selectById(peerUserId);
        if (peer == null) {
            throw new ApiException("聊天对象不存在");
        }

        Page<PrivateMessage> entityPage = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<PrivateMessage>()
                .and(w -> w
                        .eq(PrivateMessage::getFromUserId, self.getId()).eq(PrivateMessage::getToUserId, peerUserId)
                        .or()
                        .eq(PrivateMessage::getFromUserId, peerUserId).eq(PrivateMessage::getToUserId, self.getId()))
                .orderByDesc(PrivateMessage::getCreatedAt);

        privateMessageMapper.selectPage(entityPage, wrapper);

        List<PrivateMessage> records = entityPage.getRecords();
        Set<String> userIds = new LinkedHashSet<>();
        for (PrivateMessage record : records) {
            userIds.add(record.getFromUserId());
            userIds.add(record.getToUserId());
        }
        List<User> users = userIds.isEmpty() ? new ArrayList<>() : userMapper.selectBatchIds(userIds);
        java.util.Map<String, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        List<PrivateMessageVO> voList = records.stream()
                .map(item -> toVO(item, userMap.get(item.getFromUserId()), userMap.get(item.getToUserId())))
                .collect(Collectors.toList());

        Page<PrivateMessageVO> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    @Override
    public List<PrivateChatSessionVO> getSessionList(String userName) {
        User self = userService.getUserByUsername(userName);
        return privateMessageMapper.selectSessionList(self.getId());
    }

    @Override
    public Integer markRead(String userName, String peerUserId) {
        User self = userService.getUserByUsername(userName);
        User peer = userMapper.selectById(peerUserId);
        if (peer == null) {
            throw new ApiException("聊天对象不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<PrivateMessage> wrapper = new LambdaUpdateWrapper<PrivateMessage>()
                .eq(PrivateMessage::getFromUserId, peerUserId)
                .eq(PrivateMessage::getToUserId, self.getId())
                .eq(PrivateMessage::getIsRead, 0)
                .set(PrivateMessage::getIsRead, 1)
                .set(PrivateMessage::getReadAt, now);
        return privateMessageMapper.update(null, wrapper);
    }

    @Override
    public Integer getUnreadCount(String userName) {
        User self = userService.getUserByUsername(userName);
        return privateMessageMapper.selectCount(new LambdaQueryWrapper<PrivateMessage>()
                .eq(PrivateMessage::getToUserId, self.getId())
                .eq(PrivateMessage::getIsRead, 0));
    }

    private PrivateMessageVO saveAndDispatch(String fromUserId, String toUserId, String content) {
        if (StringUtils.isBlank(fromUserId) || StringUtils.isBlank(toUserId)) {
            throw new ApiException("发送方或接收方不能为空");
        }
        if (StringUtils.isBlank(content)) {
            throw new ApiException("消息内容不能为空");
        }

        User fromUser = userMapper.selectById(fromUserId);
        User toUser = userMapper.selectById(toUserId);
        if (fromUser == null || toUser == null) {
            throw new ApiException("用户不存在");
        }

        PrivateMessage message = PrivateMessage.builder()
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .content(content.trim())
                .isRead(0)
                .createdAt(LocalDateTime.now())
                .build();
        privateMessageMapper.insert(message);

        PrivateMessageVO vo = toVO(message, fromUser, toUser);
        Map<String, Object> payload = new HashMap<>(4);
        payload.put("type", "private_message");
        payload.put("data", vo);
        String jsonPayload = JSON.toJSONString(payload);

        ChatWebSocketServer.sendToUser(toUserId, jsonPayload);
        ChatWebSocketServer.sendToUser(fromUserId, jsonPayload);
        return vo;
    }

    private PrivateMessageVO toVO(PrivateMessage message, User fromUser, User toUser) {
        return PrivateMessageVO.builder()
                .id(message.getId())
                .fromUserId(message.getFromUserId())
                .fromUsername(fromUser == null ? null : fromUser.getUsername())
                .fromAlias(fromUser == null ? null : fromUser.getAlias())
                .fromAvatar(fromUser == null ? null : fromUser.getAvatar())
                .toUserId(message.getToUserId())
                .toUsername(toUser == null ? null : toUser.getUsername())
                .toAlias(toUser == null ? null : toUser.getAlias())
                .toAvatar(toUser == null ? null : toUser.getAvatar())
                .content(message.getContent())
                .isRead(message.getIsRead() != null && message.getIsRead() == 1)
                .createdAt(message.getCreatedAt())
                .readAt(message.getReadAt())
                .build();
    }
}
