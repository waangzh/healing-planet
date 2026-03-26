package com.green.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivateChatSessionVO {

    private String peerUserId;

    private String peerUsername;

    private String peerAlias;

    private String peerAvatar;

    private String lastMessage;

    @JsonFormat(locale = "zh", timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastMessageTime;

    private Integer unreadCount;
}
