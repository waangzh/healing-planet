package com.green.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum NotifyType {
    COMMENT(1, "评论了你的帖子"),
    LIKE(2, "点赞了你的帖子"),
    COLLECT(3, "收藏了你的帖子"),
    FOLLOW(4, "关注了你"),
    REPLY(5,"回复了你");

    private final Integer code;
    private final String value;


    public static String getByCode(Integer code) {
        for (NotifyType type : NotifyType.values()) {
            if (type.getCode().equals(code)) {
                return type.value;
            }
        }
        throw new IllegalArgumentException("不存在相关通知类型" + code);
    }
}
