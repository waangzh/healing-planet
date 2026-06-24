package com.example.demos.web.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 后端统一返回结果
 * @param <T>
 */
@Data
public class Result<T> implements Serializable {
    private String code;// 1成功，0和其它数字为失败
    private String msg;//错误消息
    private T data;


    public static Result success() {
        Result result = new Result<>();
        result.setCode("1");
        result.setMsg("成功");
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode("1");
        result.data = data;
        result.setMsg("成功");
        return result;
    }

    public static <T> Result<T> error(String msg) {
        Result result = new Result();
        result.setCode("0");
        result.setMsg(msg);
        return result;
    }
}
