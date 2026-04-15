package com.green.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PrivateMessageSendDTO implements Serializable {
    private static final long serialVersionUID = 3198159127938855073L;

    private String toUserId;

    private String content;
}
