package com.c203.limit.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 오류 응답의 error.code 값과 HTTP 상태를 한곳에서 관리한다.
 * 자원별 세부 코드(XXX_NOT_FOUND 등)는 공통 코드로 통합했고,
 * 클라이언트 분기가 실제로 필요한 비즈니스 코드만 유지한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값 검증에 실패했습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력 값이 올바르지 않습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문을 해석할 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 존재하는 리소스입니다."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 상태 전이입니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "동일한 Idempotency-Key로 다른 요청이 처리됐습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),

    // 인증·회원
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다. 다시 로그인해 주세요."),
    SOCIAL_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "소셜 인증에 실패했습니다."),
    SOCIAL_ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "이미 다른 계정에 연결된 소셜 계정입니다."),
    LOGIN_RESTRICTED(HttpStatus.FORBIDDEN, "로그인이 제한된 계정입니다."),
    LOGIN_ATTEMPT_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 제한됐습니다. 잠시 후 다시 시도해 주세요."),
    MEMBER_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴한 회원입니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다."),

    // 상품·주문·결제·환불
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    PURCHASE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "구매 가능 수량을 초과했습니다."),
    ORDER_NOT_PAYABLE(HttpStatus.CONFLICT, "결제할 수 없는 주문 상태입니다."),
    ORDER_NOT_CANCELLABLE(HttpStatus.BAD_REQUEST, "취소할 수 없는 주문 상태입니다."),
    AMOUNT_MISMATCH(HttpStatus.CONFLICT, "요청 금액이 일치하지 않습니다."),
    PAYMENT_NOT_REFUNDABLE(HttpStatus.CONFLICT, "환불할 수 없는 결제입니다."),
    REFUND_AMOUNT_EXCEEDED(HttpStatus.CONFLICT, "환불 가능 금액을 초과했습니다."),
    INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "서명 검증에 실패했습니다."),

    // 재고·대기열
    RESERVATION_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "만료된 재고 예약입니다."),
    INVENTORY_NOT_DEPLETED(HttpStatus.CONFLICT, "아직 재고가 남아 있습니다."),
    QUEUE_NOT_ADMITTED(HttpStatus.UNPROCESSABLE_ENTITY, "아직 입장 순서가 아닙니다."),
    DUPLICATE_QUEUE_PARTICIPATION(HttpStatus.CONFLICT, "이미 대기열에 참여 중입니다."),
    PURCHASE_PASS_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "유효하지 않은 구매권입니다."),
    CAPTCHA_VERIFICATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "보안 문자 검증에 실패했습니다."),

    // 파일·운영
    INVALID_FILE(HttpStatus.BAD_REQUEST, "유효하지 않은 파일입니다."),
    MALICIOUS_FILE_DETECTED(HttpStatus.UNPROCESSABLE_ENTITY, "허용되지 않는 파일이 감지됐습니다."),
    WITHDRAWAL_BLOCKED(HttpStatus.CONFLICT, "탈퇴를 진행할 수 없는 상태입니다."),
    LAST_SUPER_ADMIN(HttpStatus.CONFLICT, "마지막 최고 관리자는 변경할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
