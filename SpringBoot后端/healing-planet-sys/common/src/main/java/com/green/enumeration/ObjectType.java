package com.green.enumeration;


import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对象类型
 */
@AllArgsConstructor
@Getter
public enum ObjectType {
    POST(1, "帖子"),
    COMMENT(2, "评论"),
    USER(3, "用户");
    private final Integer code;
    private final String value;

    public static String getValue(Integer code) {
        for (ObjectType ob : ObjectType.values()) {
            if (ob.code.equals(code)) {
                return ob.getValue();
            }
        }
        throw new IllegalArgumentException("不存在相关对象类型: " + code);

    }
}
