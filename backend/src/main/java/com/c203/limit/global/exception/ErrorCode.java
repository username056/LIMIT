package com.c203.limit.global.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // ========== 공통 에러 ==========
    VALIDATION_FAILED("CMN001", HttpStatus.BAD_REQUEST, "입력값 검증에 실패했습니다."),
    INTERNAL_ERROR("CMN002", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE("CMN003", HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    INVALID_TYPE_VALUE("CMN004", HttpStatus.BAD_REQUEST, "잘못된 타입의 값입니다."),
    MISSING_REQUEST_PARAMETER("CMN005", HttpStatus.BAD_REQUEST, "필수 요청 값이 누락되었습니다."),
    UNAUTHORIZED("CMN006", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    TOO_MANY_REQUEST("CMN007", HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),

    // ========== 인증 에러 ==========
    INVALID_CREDENTIALS("AUTH001", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN("AUTH002", HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 토큰입니다."),
    EXPIRED_TOKEN("AUTH003", HttpStatus.UNAUTHORIZED, "만료된 인증 토큰입니다."),
    REVOKED_TOKEN("AUTH004", HttpStatus.UNAUTHORIZED, "폐기된 인증 토큰입니다."),
    FORBIDDEN("AUTH005", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // ========== 회원 에러 ==========
    MEMBER_NOT_FOUND("MEM001", HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    EMAIL_DUPLICATED("MEM002", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_DUPLICATED("MEM003", HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    MEMBER_NOT_ACTIVE("MEM004", HttpStatus.FORBIDDEN, "이용할 수 없는 회원 계정입니다."),
    INVALID_PASSWORD_FORMAT("MEM005", HttpStatus.BAD_REQUEST, "비밀번호 형식이 올바르지 않습니다."),
    CURRENT_PASSWORD_MISMATCH("MEM006", HttpStatus.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다."),
    SAME_AS_OLD_PASSWORD("MEM007", HttpStatus.CONFLICT, "기존 비밀번호와 다른 비밀번호를 입력해 주세요."),
    SOCIAL_MEMBER_PASSWORD_UNAVAILABLE("MEM008", HttpStatus.UNPROCESSABLE_ENTITY, "소셜 전용 회원은 비밀번호를 변경할 수 없습니다."),
    UNSUPPORTED_SOCIAL_PROVIDER("AUTH006", HttpStatus.BAD_REQUEST, "지원하지 않거나 구성되지 않은 소셜 로그인 공급자입니다."),
    SOCIAL_ACCOUNT_NOT_FOUND("AUTH007", HttpStatus.NOT_FOUND, "연동된 소셜 계정을 찾을 수 없습니다."),
    LAST_LOGIN_METHOD("AUTH008", HttpStatus.CONFLICT, "마지막 로그인 수단은 해제할 수 없습니다."),

    // ========== 채팅 에러 ==========
    LISTING_NOT_FOUND("CHT001", HttpStatus.NOT_FOUND, "매물을 찾을 수 없습니다."),
    SELF_CHAT_NOT_ALLOWED("CHT002", HttpStatus.BAD_REQUEST, "본인의 매물에는 채팅방을 생성할 수 없습니다."),
    CHAT_ROOM_CREATION_NOT_ALLOWED("CHT003", HttpStatus.CONFLICT, "현재 상태의 매물에는 채팅방을 생성할 수 없습니다."),

    SELLER_APPLICATION_NOT_FOUND("SEL001", HttpStatus.NOT_FOUND, "판매자 신청서를 찾을 수 없습니다."),
    ACTIVE_SELLER_APPLICATION_EXISTS("SEL002", HttpStatus.CONFLICT, "처리 중인 판매자 신청서가 있습니다."),
    ALREADY_SELLER("SEL003", HttpStatus.CONFLICT, "이미 판매자 권한을 가진 회원입니다."),
    SELLER_APPLICATION_NOT_EDITABLE("SEL004", HttpStatus.CONFLICT, "수정할 수 없는 판매자 신청서 상태입니다."),
    SELLER_APPLICATION_INCOMPLETE("SEL005", HttpStatus.BAD_REQUEST, "판매자 신청 필수 정보와 증빙을 확인해 주세요."),
    SELLER_APPLICATION_NOT_CANCELABLE("SEL006", HttpStatus.CONFLICT, "취소할 수 없는 판매자 신청서 상태입니다."),
    SELLER_DOCUMENT_NOT_FOUND("SEL007", HttpStatus.NOT_FOUND, "판매자 증빙 문서를 찾을 수 없습니다."),
    INVALID_SELLER_DOCUMENT("SEL008", HttpStatus.BAD_REQUEST, "허용되지 않는 판매자 증빙 문서입니다."),
    SELLER_PROFILE_NOT_FOUND("SEL009", HttpStatus.NOT_FOUND, "판매자 프로필을 찾을 수 없습니다."),
    ADMIN_NOT_FOUND("ADM001", HttpStatus.NOT_FOUND, "관리자 계정을 찾을 수 없습니다."),
    ADMIN_ROLE_REQUIRED("ADM002", HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
    MEMBER_RESTRICTION_NOT_FOUND("ADM003", HttpStatus.NOT_FOUND, "회원 이용 제한을 찾을 수 없습니다."),
    INVALID_RESTRICTION_PERIOD("ADM004", HttpStatus.BAD_REQUEST, "이용 제한 기간이 올바르지 않습니다."),
    OVERLAPPING_RESTRICTION("ADM005", HttpStatus.CONFLICT, "같은 유형의 활성 이용 제한이 존재합니다."),
    RESTRICTION_NOT_ACTIVE("ADM006", HttpStatus.CONFLICT, "활성 상태의 이용 제한만 해제할 수 있습니다."),
    ADMIN_ACTION_LOG_NOT_FOUND("ADM007", HttpStatus.NOT_FOUND, "관리자 처리 이력을 찾을 수 없습니다."),
    INQUIRY_NOT_FOUND("ADM008", HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다."),
    WITHDRAWAL_REQUEST_NOT_FOUND("ADM009", HttpStatus.NOT_FOUND, "회원 탈퇴 요청을 찾을 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
