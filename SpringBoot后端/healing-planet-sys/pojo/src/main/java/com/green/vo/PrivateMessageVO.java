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
public class PrivateMessageVO {

    private String id;

    private String fromUserId;

    private String fromUsername;

    private String fromAlias;

    private String fromAvatar;

    private String toUserId;

    private String toUsername;

    private String toAlias;

    private String toAvatar;

    private String content;

    private Boolean isRead;

    @JsonFormat(locale = "zh", timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(locale = "zh", timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readAt;
}
