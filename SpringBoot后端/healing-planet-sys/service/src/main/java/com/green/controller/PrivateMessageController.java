package com.green.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.common.api.Result;
import com.green.dto.PrivateMessageSendDTO;
import com.green.service.IPrivateMessageService;
import com.green.vo.PrivateChatSessionVO;
import com.green.vo.PrivateMessageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.green.security.jwt.JwtUtil.USER_NAME;

@RestController
@RequestMapping("/chat/private")
public class PrivateMessageController {

    @Autowired
    private IPrivateMessageService privateMessageService;

    @PostMapping("/send")
    public Result<PrivateMessageVO> send(@RequestHeader(value = USER_NAME) String userName,
                                         @RequestBody PrivateMessageSendDTO dto) {
        return Result.success(privateMessageService.sendByHttp(userName, dto));
    }

    @GetMapping("/sessions")
    public Result<List<PrivateChatSessionVO>> sessionList(@RequestHeader(value = USER_NAME) String userName) {
        return Result.success(privateMessageService.getSessionList(userName));
    }

    @GetMapping("/messages")
    public Result<Page<PrivateMessageVO>> messages(@RequestHeader(value = USER_NAME) String userName,
                                                   @RequestParam("peerUserId") String peerUserId,
                                                   @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                                   @RequestParam(value = "size", defaultValue = "20") Integer pageSize) {
        return Result.success(privateMessageService.getConversation(userName, peerUserId, pageNo, pageSize));
    }

    @PutMapping("/read")
    public Result<Integer> markRead(@RequestHeader(value = USER_NAME) String userName,
                                    @RequestParam("peerUserId") String peerUserId) {
        return Result.success(privateMessageService.markRead(userName, peerUserId));
    }

    @GetMapping("/unread/count")
    public Result<Integer> unreadCount(@RequestHeader(value = USER_NAME) String userName) {
        return Result.success(privateMessageService.getUnreadCount(userName));
    }

    @DeleteMapping("/session")
    public Result<Integer> deleteSession(@RequestHeader(value = USER_NAME) String userName,
                                         @RequestParam("peerUserId") String peerUserId) {
        return Result.success(privateMessageService.deleteConversation(userName, peerUserId));
    }
}
