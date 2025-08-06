package com.green.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CollectVO {

    /**
     * 文章id
     */
    private String topicId;

    /**
     * 文章标题
     */
    private String topic;

}
