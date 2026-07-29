package com.c203.limit.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    SOCIAL_ACCOUNT_REAUTH_REQUIRED(
            "AUTH014", HttpStatus.CONFLICT, "이미 가입된 이메일입니다. 기존 계정 로그인 후 연동해 주세요."),
    SOCIAL_SIGNUP_SESSION_INVALID(
            "AUTH015", HttpStatus.UNAUTHORIZED, "소셜 회원가입 세션이 만료되었거나 유효하지 않습니다."),
    REQUIRED_TERMS_NOT_ACCEPTED("AUTH016", HttpStatus.BAD_REQUEST, "필수 약관에 모두 동의해야 합니다."),
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
    SOCIAL_MEMBER_PASSWORD_UNAVAILABLE(
            "MEM008", HttpStatus.UNPROCESSABLE_ENTITY, "소셜 전용 회원은 비밀번호를 변경할 수 없습니다."),
    UNSUPPORTED_SOCIAL_PROVIDER(
            "AUTH006", HttpStatus.BAD_REQUEST, "지원하지 않거나 구성되지 않은 소셜 로그인 공급자입니다."),
    SOCIAL_ACCOUNT_NOT_FOUND("AUTH007", HttpStatus.NOT_FOUND, "연동된 소셜 계정을 찾을 수 없습니다."),
    LAST_LOGIN_METHOD("AUTH008", HttpStatus.CONFLICT, "마지막 로그인 수단은 해제할 수 없습니다."),
    EMAIL_NOT_VERIFIED("AUTH009", HttpStatus.FORBIDDEN, "이메일 인증이 필요합니다."),
    EMAIL_VERIFICATION_TOKEN_INVALID(
            "AUTH010", HttpStatus.BAD_REQUEST, "이메일 인증 토큰이 만료되었거나 유효하지 않습니다."),
    EMAIL_VERIFICATION_UNAVAILABLE(
            "AUTH011", HttpStatus.SERVICE_UNAVAILABLE, "이메일 인증 발송 설정을 확인해 주세요."),
    SOCIAL_AUTH_FAILED("AUTH012", HttpStatus.UNAUTHORIZED, "소셜 로그인 인증에 실패했습니다."),
    SOCIAL_ACCOUNT_CONFLICT("AUTH013", HttpStatus.CONFLICT, "이미 다른 소셜 계정이 연결되어 있습니다."),
    PASSWORD_RESET_TOKEN_INVALID(
            "AUTH017", HttpStatus.BAD_REQUEST, "비밀번호 재설정 토큰이 만료되었거나 유효하지 않습니다."),
    PASSWORD_RESET_UNAVAILABLE(
            "AUTH018", HttpStatus.SERVICE_UNAVAILABLE, "비밀번호 재설정 메일 발송 설정을 확인해 주세요."),

    // ========== 채팅 에러 ==========
    LISTING_NOT_FOUND("CHT001", HttpStatus.NOT_FOUND, "매물을 찾을 수 없습니다."),
    SELF_CHAT_NOT_ALLOWED("CHT002", HttpStatus.BAD_REQUEST, "본인의 매물에는 채팅방을 생성할 수 없습니다."),
    CHAT_ROOM_CREATION_NOT_ALLOWED("CHT003", HttpStatus.CONFLICT, "현재 상태의 매물에는 채팅방을 생성할 수 없습니다."),
    CHAT_ROOM_ACCESS_DENIED("CHT004", HttpStatus.FORBIDDEN, "채팅방에 접근할 권한이 없습니다."),
    CHAT_MEDIA_INVALID("CHT005", HttpStatus.UNPROCESSABLE_ENTITY, "지원하지 않거나 허용 용량을 초과한 채팅 파일입니다."),
    CHAT_MEDIA_NOT_FOUND("CHT006", HttpStatus.NOT_FOUND, "채팅 파일을 찾을 수 없습니다."),

    // ========== 실시간 확인 오류 ==========
    RTC_SESSION_NOT_FOUND("RTC001", HttpStatus.NOT_FOUND, "실시간 확인 세션을 찾을 수 없습니다."),
    RTC_SESSION_ACCESS_DENIED("RTC002", HttpStatus.FORBIDDEN, "실시간 확인 세션에 접근할 권한이 없습니다."),
    RTC_SESSION_EXPIRED("RTC003", HttpStatus.GONE, "실시간 확인 세션이 만료되었습니다."),
    RTC_SESSION_CLOSED("RTC004", HttpStatus.CONFLICT, "이미 종료된 실시간 확인 세션입니다."),
    RTC_INVALID_STATE("RTC005", HttpStatus.CONFLICT, "현재 상태에서는 실시간 확인 요청을 처리할 수 없습니다."),

    SELLER_APPLICATION_NOT_FOUND("SEL001", HttpStatus.NOT_FOUND, "판매자 신청서를 찾을 수 없습니다."),
    ACTIVE_SELLER_APPLICATION_EXISTS("SEL002", HttpStatus.CONFLICT, "처리 중인 판매자 신청서가 있습니다."),
    ALREADY_SELLER("SEL003", HttpStatus.CONFLICT, "이미 판매자 권한을 가진 회원입니다."),
    SELLER_APPLICATION_NOT_EDITABLE("SEL004", HttpStatus.CONFLICT, "수정할 수 없는 판매자 신청서 상태입니다."),
    SELLER_APPLICATION_INCOMPLETE("SEL005", HttpStatus.BAD_REQUEST, "판매자 신청 필수 정보와 증빙을 확인해 주세요."),
    SELLER_APPLICATION_NOT_CANCELABLE("SEL006", HttpStatus.CONFLICT, "취소할 수 없는 판매자 신청서 상태입니다."),
    SELLER_DOCUMENT_NOT_FOUND("SEL007", HttpStatus.NOT_FOUND, "판매자 증빙 문서를 찾을 수 없습니다."),
    INVALID_SELLER_DOCUMENT("SEL008", HttpStatus.BAD_REQUEST, "허용되지 않는 판매자 증빙 문서입니다."),
    SELLER_PROFILE_NOT_FOUND("SEL009", HttpStatus.NOT_FOUND, "판매자 프로필을 찾을 수 없습니다."),
    SELLER_NOT_ACTIVE("SEL010", HttpStatus.FORBIDDEN, "활성 판매자만 이용할 수 있습니다."),
    ADMIN_NOT_FOUND("ADM001", HttpStatus.NOT_FOUND, "관리자 계정을 찾을 수 없습니다."),
    ADMIN_ROLE_REQUIRED("ADM002", HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
    MEMBER_RESTRICTION_NOT_FOUND("ADM003", HttpStatus.NOT_FOUND, "회원 이용 제한을 찾을 수 없습니다."),
    INVALID_RESTRICTION_PERIOD("ADM004", HttpStatus.BAD_REQUEST, "이용 제한 기간이 올바르지 않습니다."),
    OVERLAPPING_RESTRICTION("ADM005", HttpStatus.CONFLICT, "같은 유형의 활성 이용 제한이 존재합니다."),
    RESTRICTION_NOT_ACTIVE("ADM006", HttpStatus.CONFLICT, "활성 상태의 이용 제한만 해제할 수 있습니다."),
    ADMIN_ACTION_LOG_NOT_FOUND("ADM007", HttpStatus.NOT_FOUND, "관리자 처리 이력을 찾을 수 없습니다."),
    INQUIRY_NOT_FOUND("ADM008", HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다."),
    WITHDRAWAL_REQUEST_NOT_FOUND("ADM009", HttpStatus.NOT_FOUND, "회원 탈퇴 요청을 찾을 수 없습니다."),
    ADMIN_EMAIL_DUPLICATED("ADM010", HttpStatus.CONFLICT, "이미 사용 중인 관리자 이메일입니다."),
    LAST_SUPER_ADMIN("ADM011", HttpStatus.CONFLICT, "마지막 활성 최고 관리자는 변경할 수 없습니다."),
    INVALID_ADMIN_ROLE("ADM012", HttpStatus.BAD_REQUEST, "관리자 권한은 OPERATOR 또는 SUPER_ADMIN이어야 합니다."),
    INVALID_ADMIN_STATUS("ADM013", HttpStatus.BAD_REQUEST, "관리자 상태가 올바르지 않습니다."),
    ADMIN_CURRENT_PASSWORD_MISMATCH("ADM014", HttpStatus.UNAUTHORIZED, "현재 관리자 비밀번호가 일치하지 않습니다."),
    ADMIN_SAME_AS_OLD_PASSWORD("ADM015", HttpStatus.CONFLICT, "기존 관리자 비밀번호와 다른 비밀번호를 입력해 주세요."),

    // ========== 매물(상품) 에러 ==========
    LISTING_NOT_ON_SALE("PRD001", HttpStatus.CONFLICT, "판매중 상태의 매물만 예약할 수 있습니다."),
    LISTING_NOT_RESERVED("PRD002", HttpStatus.CONFLICT, "예약중 상태의 매물에만 적용할 수 있는 작업입니다."),
    LISTING_NOT_PAID("PRD003", HttpStatus.CONFLICT, "결제완료 상태의 매물만 검수를 시작할 수 있습니다."),
    LISTING_NOT_INSPECTING("PRD004", HttpStatus.CONFLICT, "검수중 상태의 매물만 확정할 수 있습니다."),
    LISTING_NOT_CONFIRMED("PRD005", HttpStatus.CONFLICT, "확정 상태의 매물만 정산할 수 있습니다."),
    PRODUCT_EDIT_NOT_ALLOWED("PRD006", HttpStatus.CONFLICT, "초안 상태의 상품만 수정할 수 있습니다."),
    PRODUCT_DELETE_NOT_ALLOWED("PRD007", HttpStatus.CONFLICT, "현재 상태의 상품은 삭제할 수 없습니다."),
    INVALID_PRODUCT_STATUS_TRANSITION("PRD008", HttpStatus.CONFLICT, "허용되지 않은 상품 상태 전환입니다."),
    REQUIRED_EVIDENCE_INCOMPLETE("PRD009", HttpStatus.UNPROCESSABLE_ENTITY, "필수 체크리스트를 완료해 주세요."),
    DEVICE_MODEL_NOT_FOUND("PRD010", HttpStatus.NOT_FOUND, "기기 모델을 찾을 수 없습니다."),
    CHECKLIST_TEMPLATE_NOT_FOUND("PRD011", HttpStatus.NOT_FOUND, "게시된 체크리스트 템플릿을 찾을 수 없습니다."),
    PRODUCT_ACCESS_DENIED("PRD012", HttpStatus.FORBIDDEN, "해당 상품을 변경할 권한이 없습니다."),
    CHECKLIST_DEVICE_TYPE_NOT_SUPPORTED(
            "PRD013", HttpStatus.BAD_REQUEST, "현재 자동 체크리스트 생성은 노트북만 지원합니다."),
    CHECKLIST_OS_NOT_SUPPORTED(
            "PRD014", HttpStatus.BAD_REQUEST, "노트북 체크리스트는 Windows와 Linux만 지원합니다."),

    // ========== 결제 에러 ==========
    PAYMENT_NOT_FOUND("PAY001", HttpStatus.NOT_FOUND, "결제 내역을 찾을 수 없습니다."),
    SELF_PURCHASE_NOT_ALLOWED("PAY002", HttpStatus.BAD_REQUEST, "본인의 매물은 결제할 수 없습니다."),
    PAYMENT_ACCESS_DENIED("PAY003", HttpStatus.FORBIDDEN, "해당 결제 내역을 조회할 권한이 없습니다."),
    IDEMPOTENCY_KEY_CONFLICT("PAY004", HttpStatus.CONFLICT, "동일한 멱등키로 다른 내용의 결제 요청이 이미 존재합니다."),
    PAYMENT_REQUEST_CONFLICT(
            "PAY005", HttpStatus.CONFLICT, "결제 요청이 다른 요청과 경합해 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."),

    // ========== 장소 검색 에러 ==========
    PLACE_SEARCH_UNAVAILABLE("PLC001", HttpStatus.SERVICE_UNAVAILABLE, "장소 검색 설정을 확인해 주세요."),

    // ========== 검수/OCR 에러 ==========
    EVIDENCE_NOT_FOUND("INS001", HttpStatus.NOT_FOUND, "증거를 찾을 수 없습니다."),
    EVIDENCE_NOT_READY("INS002", HttpStatus.CONFLICT, "아직 처리되지 않은 증거입니다."),
    OCR_UNSUPPORTED_IMAGE_FORMAT("INS003", HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다."),
    OCR_IMAGE_FETCH_FAILED("INS004", HttpStatus.BAD_GATEWAY, "증거 이미지를 불러오지 못했습니다."),
    OCR_REQUEST_FAILED("INS005", HttpStatus.BAD_GATEWAY, "OCR 요청에 실패했습니다."),
    OCR_RECOGNITION_FAILED("INS006", HttpStatus.UNPROCESSABLE_ENTITY, "이미지에서 텍스트를 인식하지 못했습니다."),
    INVALID_EVIDENCE_TYPE("INS007", HttpStatus.BAD_REQUEST, "지원하지 않는 증거 유형입니다."),
    DXDIAG_FILE_FETCH_FAILED("INS008", HttpStatus.BAD_GATEWAY, "증거 파일을 불러오지 못했습니다."),
    BATTERY_REPORT_FILE_FETCH_FAILED("INS009", HttpStatus.BAD_GATEWAY, "증거 파일을 불러오지 못했습니다."),
    PARSING_FAILED("INS010", HttpStatus.UNPROCESSABLE_ENTITY, "OCR 자동 구조화에 실패했습니다."),
    UNSUPPORTED_FILE_FORMAT("INS011", HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    ITEM_NOT_FOUND("INS012", HttpStatus.NOT_FOUND, "체크리스트 항목을 찾을 수 없습니다."),
    FIELD_NOT_EDITABLE("INS013", HttpStatus.BAD_REQUEST, "수정할 수 없는 필드입니다."),
    PRODUCT_NOT_FOUND("INS014", HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    ALREADY_PARSED("INS015", HttpStatus.CONFLICT, "이미 파싱된 증거입니다."),
    REINSPECTION_SELF_REQUEST_NOT_ALLOWED(
            "INS016", HttpStatus.BAD_REQUEST, "본인 매물에는 재검수를 요청할 수 없습니다."),
    REINSPECTION_ITEM_LISTING_MISMATCH(
            "INS017", HttpStatus.BAD_REQUEST, "선택한 체크리스트 항목이 해당 매물 소속이 아닙니다."),
    REINSPECTION_REQUEST_NOT_FOUND("INS018", HttpStatus.NOT_FOUND, "재검수 요청을 찾을 수 없습니다."),
    REINSPECTION_ACCESS_DENIED("INS019", HttpStatus.FORBIDDEN, "해당 재검수 요청을 처리할 권한이 없습니다."),
    REINSPECTION_ALREADY_PROCESSED("INS020", HttpStatus.CONFLICT, "이미 처리된 재검수 요청입니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
