package com.green.common.exception;//package com.knox.aurora.common.exception;

import com.green.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Slf4j
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


    /**
     * 处理所有其他未被捕获的通用异常
     */
    @ExceptionHandler(value = Exception.class)
    public Result<?> handleException(Exception ex) {
        // 打印详细异常堆栈
        log.error("系统异常", ex);

        return Result.failed("对不起，操作失败，请检查后再操作");
    }
}
