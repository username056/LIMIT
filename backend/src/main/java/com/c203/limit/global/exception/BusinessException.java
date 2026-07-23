package com.c203.limit.global.exception;

import java.util.Objects;
import lombok.Getter;

/** 도메인 규칙 위반을 표현하는 공통 예외. ErrorCode로 HTTP 상태와 오류 코드를 결정한다. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(resolveMessage(errorCode, message));
        this.errorCode = errorCode;
    }

    private static String resolveMessage(ErrorCode errorCode, String message) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return message == null || message.isBlank() ? errorCode.getMessage() : message;
    }
}
