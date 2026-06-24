package com.example.demos.web.handler;

import com.example.demos.web.common.result.Result;
import com.example.demos.web.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理自定义的业务异常 (BaseException)
     */
    @ExceptionHandler(BaseException.class)
    public Result handleBaseException(BaseException ex) {
        log.error("业务异常：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 处理所有其他未被捕获的通用异常
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception ex) {
        // 打印详细异常堆栈
        log.error("系统异常", ex);

        return Result.error("对不起，操作失败，请检查后再操作");
    }
}