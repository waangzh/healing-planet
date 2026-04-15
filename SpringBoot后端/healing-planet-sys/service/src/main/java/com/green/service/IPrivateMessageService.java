package com.green.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.dto.PrivateMessageSendDTO;
import com.green.vo.PrivateChatSessionVO;
import com.green.vo.PrivateMessageVO;

import java.util.List;

public interface IPrivateMessageService {

    PrivateMessageVO sendByHttp(String senderUserName, PrivateMessageSendDTO dto);

    void handleWebSocketSend(String fromUserId, String toUserId, String content);

    Page<PrivateMessageVO> getConversation(String userName, String peerUserId, Integer pageNo, Integer pageSize);

    List<PrivateChatSessionVO> getSessionList(String userName);

    Integer markRead(String userName, String peerUserId);

    Integer getUnreadCount(String userName);

    Integer deleteConversation(String userName, String peerUserId);
}
