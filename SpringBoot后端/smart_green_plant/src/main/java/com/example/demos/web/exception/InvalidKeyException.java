package com.example.demos.web.exception;

/**
 * key值不正确异常
 */

public class InvalidKeyException extends RuntimeException {
    public InvalidKeyException(String message) {
        super(message);
    }
}
