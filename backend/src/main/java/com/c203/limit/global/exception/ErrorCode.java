package com.c203.limit.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 공통 오류의 외부 코드, HTTP 상태와 메시지를 관리한다. 도메인 오류는 각 기능 구현 시 추가한다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ========== 공통 에러 ==========
    VALIDATION_FAILED("CMN001", HttpStatus.BAD_REQUEST, "입력값 검증에 실패했습니다."),
    INTERNAL_ERROR("CMN002", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE("CMN003", HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    INVALID_TYPE_VALUE("CMN004", HttpStatus.BAD_REQUEST, "잘못된 타입의 값입니다."),
    MISSING_REQUEST_PARAMETER("CMN005", HttpStatus.BAD_REQUEST, "필수 요청 파라미터가 누락되었습니다."),
    UNAUTHORIZED("CMN006", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    TOO_MANY_REQUEST("CMN007", HttpStatus.TOO_MANY_REQUESTS,
            "현재 많은 사용자가 접속 중입니다. 잠시 후 다시 시도해 주세요.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
