package com.example.demos.web.exception;

import com.example.demos.web.common.enums.ResultCodeEnum;
import lombok.Data;


public class CustomException extends RuntimeException {
    private String code;
    private String msg;

    public CustomException(ResultCodeEnum resultCodeEnum) {
        this.code = resultCodeEnum.code;
        this.msg = resultCodeEnum.msg;
    }

    public CustomException(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public String getMessage() {
        return msg;
    }

}
