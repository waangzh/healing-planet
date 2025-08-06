package com.green.common.exception;//package com.knox.aurora.common.exception;

import com.green.common.api.Result;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 捕获自定义异常
     */
    @ResponseBody
    @ExceptionHandler(value = ApiException.class)
    public Result<Map<String, Object>> handle(ApiException e) {
        if (e.getErrorCode() != null) {
            return Result.failed(e.getErrorCode());
        }
        return Result.failed(e.getMessage());
    }
}
