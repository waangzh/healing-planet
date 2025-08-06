package com.green.dto;


import lombok.Data;

import java.io.Serializable;

@Data
public class CollectDTO implements Serializable {

    /**
     * 用户名
     */
    private String userName;
    /**
     * 文章id
     */
    private String topicId;
}
