package com.green.common.exception;

import com.green.common.api.IErrorCode;
import lombok.Getter;


@Getter
public class ApiException extends RuntimeException {
    private IErrorCode errorCode;

    public ApiException(IErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ApiException(String message) {
        super(message);
    }

}
