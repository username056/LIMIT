#!/usr/bin/env python3
"""Generate domain-first API contracts and preview controllers from API/DTO CSV files."""

from __future__ import annotations

import argparse
import csv
import json
import re
from collections import defaultdict
from pathlib import Path


SCHEMA_GROUPS = {
    "Admin": ("admin", "AdminSchemas"),
    "Common": ("common", "CommonSchemas"),
    "Order": ("order", "OrderSchemas"),
    "Payment": ("payment", "PaymentSchemas"),
    "Product": ("product", "ProductSchemas"),
    "PurchasePass": ("queue", "PurchasePassSchemas"),
    "Queue": ("queue", "QueueSchemas"),
    "Refund": ("refund", "RefundSchemas"),
    "Security": ("queue", "SecuritySchemas"),
    "Seller": ("seller", "SellerSchemas"),
    "Statistics": ("statistics", "StatisticsSchemas"),
    "Stock": ("stock", "StockSchemas"),
}

USER_SCHEMA_GROUPS = {
    "auth": {
        "SignupRequest", "SignupResponse", "LoginRequest", "LoginResponse",
        "TokenRefreshRequest", "TokenResponse", "LogoutRequest",
        "EmailAvailabilityResponse", "NicknameAvailabilityResponse",
        "SocialLoginRequest", "SocialLoginResponse", "SocialAccountResponse",
    },
    "member": {
        "MemberSummaryResponse", "MemberProfileResponse", "UpdateMemberRequest",
        "UpdateMemberResponse", "ChangePasswordRequest",
    },
    "address": {"CreateAddressRequest", "UpdateAddressRequest", "AddressResponse"},
    "notification": {"NotificationSettingsResponse", "UpdateNotificationSettingsRequest"},
    "withdrawal": {"CreateWithdrawalRequest", "WithdrawalRequestResponse"},
    "favorite": {"FavoriteProductResponse"},
    "cart": {
        "AddCartItemRequest", "UpdateCartItemQuantityRequest", "CartItemResponse", "CartResponse",
    },
    "inquiry": {
        "CreateInquiryRequest", "UpdateInquiryRequest", "InquirySummaryResponse",
        "InquiryAnswerResponse", "InquiryDetailResponse",
    },
}

USER_SCHEMA_CLASSES = {
    "auth": "AuthSchemas",
    "member": "MemberSchemas",
    "address": "AddressSchemas",
    "notification": "NotificationSchemas",
    "withdrawal": "WithdrawalSchemas",
    "favorite": "FavoriteSchemas",
    "cart": "CartSchemas",
    "inquiry": "InquirySchemas",
}

API_GROUPS = {
    "AUTH": ("auth", "AuthApi", "01. 인증"),
    "MEMBER": ("member", "MemberApi", "02. 회원"),
    "ADDRESS": ("address", "AddressApi", "03. 배송지"),
    "PRODUCT": ("product", "ProductApi", "04. 상품"),
    "CART": ("cart", "CartApi", "05. 장바구니"),
    "FAVORITE": ("favorite", "FavoriteApi", "06. 관심 상품"),
    "ORDER": ("order", "OrderApi", "07. 주문"),
    "PAY": ("payment", "PaymentApi", "08. 결제"),
    "REFUND": ("refund", "RefundApi", "09. 환불"),
    "STOCK": ("stock", "StockApi", "10. 재고"),
    "QUEUE": ("queue", "QueueApi", "11. 대기열"),
    "SELLER": ("seller", "SellerApi", "12. 판매자"),
    "INQUIRY": ("inquiry", "InquiryApi", "13. 문의"),
    "NOTIFICATION": ("notification", "NotificationApi", "14. 알림"),
    "WITHDRAWAL": ("withdrawal", "WithdrawalApi", "15. 출금"),
    "STATISTICS": ("statistics", "StatisticsApi", "16. 통계"),
    "ADMIN": ("admin", "AdminApi", "17. 관리자"),
}

JAVA_SCALARS = {
    "String": "String",
    "Object": "Object",
    "Long": "Long",
    "long": "long",
    "Integer": "Integer",
    "int": "int",
    "Boolean": "Boolean",
    "boolean": "boolean",
    "BigDecimal": "BigDecimal",
    "OffsetDateTime": "OffsetDateTime",
    "Instant": "Instant",
    "LocalDate": "LocalDate",
    "T": "T",
}

GLOBAL_RESPONSE_DTOS = {
    "ApiResponse",
    "ApiErrorResponse",
    "ApiErrorDetailResponse",
    "FieldErrorResponse",
}

RESTFUL_ROUTES = {
    "ADMIN-AUTH-01": ("POST", "/api/v1/admin/sessions"),
    "ADMIN-MEMBER-05": ("POST", "/api/v1/admin/member-restrictions/{restrictionId}/releases"),
    "ADMIN-SELLER-APP-04": ("POST", "/api/v1/admin/seller-applications/{applicationId}/reviews"),
    "ADMIN-SELLER-APP-05": ("POST", "/api/v1/admin/seller-applications/{applicationId}/approvals"),
    "ADMIN-SELLER-APP-06": ("POST", "/api/v1/admin/seller-applications/{applicationId}/rejections"),
    "ADDRESS-05": ("PUT", "/api/v1/members/me/default-addresses/{addressId}"),
    "AUTH-01": ("GET", "/api/v1/auth/email-availability"),
    "AUTH-02": ("GET", "/api/v1/auth/nickname-availability"),
    "AUTH-03": ("POST", "/api/v1/members"),
    "AUTH-04": ("POST", "/api/v1/auth/sessions"),
    "AUTH-05": ("POST", "/api/v1/auth/token-refreshes"),
    "AUTH-06": ("POST", "/api/v1/auth/session-revocations"),
    "AUTH-07": ("POST", "/api/v1/auth/social-sessions/{provider}"),
    "ORDER-03": ("POST", "/api/v1/orders/{orderId}/cancellations"),
    "ORDER-04": ("POST", "/api/v1/orders/{orderId}/confirmations"),
    "PAY-02": ("POST", "/api/v1/payment-webhooks/toss"),
    "PAY-03": ("POST", "/api/v1/payments/{paymentId}/failures"),
    "PRODUCT-6": ("GET", "/api/v1/sellers/me/products"),
    "PRODUCT-7": ("GET", "/api/v1/admin/products"),
    "PRODUCT-8": ("POST", "/api/v1/products/{productId}/actions"),
    "PRODUCT-9": ("POST", "/api/v1/products/{productId}/actions"),
    "PRODUCT-10": ("GET", "/api/v1/products/{productId}/actions"),
    "QUEUE-03": ("POST", "/api/v1/internal/sales/{saleId}/purchase-passes"),
    "QUEUE-04": ("POST", "/api/v1/internal/purchase-pass-expirations"),
    "REFUND-02": ("POST", "/api/v1/payments/refunds/{refundId}/completions"),
    "SELLER-APP-07": ("POST", "/api/v1/seller-applications/{applicationId}/submissions"),
    "SELLER-APP-08": ("POST", "/api/v1/seller-applications/{applicationId}/cancellations"),
    "STATISTICS-4": ("GET", "/api/v1/popular-products"),
    "STATISTICS-5": ("GET", "/api/v1/members/me/product-recommendations"),
    "STATISTICS-6": ("GET", "/api/v1/members/me/product-view-history"),
    "STOCK-02": ("POST", "/api/v1/internal/stock-reservations/{reservationId}/confirmations"),
    "STOCK-03": ("POST", "/api/v1/internal/stock-reservations/{reservationId}/releases"),
    "STOCK-04": ("POST", "/api/v1/internal/sales/{saleId}/sold-out-events"),
}

EXPLICIT_REQUESTS = {
    "AUTH-03": "SignupRequest",
    "AUTH-04": "LoginRequest",
    "AUTH-05": "TokenRefreshRequest",
    "AUTH-06": "LogoutRequest",
    "AUTH-07": "SocialLoginRequest",
    "MEMBER-02": "UpdateMemberRequest",
    "MEMBER-03": "ChangePasswordRequest",
    "ADDRESS-02": "CreateAddressRequest",
    "ADDRESS-03": "UpdateAddressRequest",
    "NOTIFICATION-02": "UpdateNotificationSettingsRequest",
    "WITHDRAWAL-01": "CreateWithdrawalRequest",
    "CART-02": "AddCartItemRequest",
    "CART-03": "UpdateCartItemQuantityRequest",
    "INQUIRY-01": "CreateInquiryRequest",
    "INQUIRY-04": "UpdateInquiryRequest",
    "SELLER-APP-01": "CreateSellerApplicationRequest",
    "SELLER-APP-04": "UpdateSellerApplicationRequest",
    "SELLER-APP-08": "CancelSellerApplicationRequest",
    "ADMIN-AUTH-01": "AdminLoginRequest",
    "ADMIN-MEMBER-04": "CreateMemberRestrictionRequest",
    "ADMIN-MEMBER-05": "ReleaseMemberRestrictionRequest",
    "ADMIN-WITHDRAWAL-03": "ProcessWithdrawalRequest",
    "ADMIN-SELLER-APP-05": "ApproveSellerApplicationRequest",
    "ADMIN-SELLER-APP-06": "RejectSellerApplicationRequest",
    "ADMIN-SELLER-03": "UpdateSellerStatusRequest",
    "ADMIN-SELLER-04": "UpdateSellerLimitsRequest",
    "ADMIN-INQUIRY-03": "UpsertInquiryAnswerRequest",
    "ADMIN-INQUIRY-04": "UpdateInquiryStatusRequest",
    "ADMIN-ROLE-02": "GrantRoleRequest",
    "ORDER-01": "CreateOrderRequest",
    "ORDER-03": "CancelOrderRequest",
    "PAY-01": "CreatePaymentRequest",
    "PAY-02": "TossWebhookPayload",
    "PAY-03": "FailPaymentRequest",
    "REFUND-01": "CreateRefundRequest",
    "REFUND-02": "CompleteRefundRequest",
    "QUEUE-01": "QueueJoinRequest",
    "QUEUE-03": "PurchasePassIssueRequest",
    "QUEUE-04": "PurchasePassExpireRequest",
    "QUEUE-05": "CaptchaVerifyRequest",
    "STOCK-01": "StockReservationCreateRequest",
    "STOCK-02": "StockReservationConfirmRequest",
    "STOCK-03": "StockReservationReleaseRequest",
    "STOCK-04": "SaleSoldOutRequest",
    "PRODUCT-1": "CreateProductRequest",
    "PRODUCT-2": "UpdateProductRequest",
    "PRODUCT-8": "CreateProductActionRequest",
    "PRODUCT-9": "CreateProductActionRequest",
}

EXPLICIT_RESPONSES = {
    "AUTH-01": "EmailAvailabilityResponse",
    "AUTH-02": "NicknameAvailabilityResponse",
    "AUTH-03": "SignupResponse",
    "AUTH-04": "LoginResponse",
    "AUTH-05": "TokenResponse",
    "AUTH-07": "SocialLoginResponse",
    "AUTH-08": "SocialAccountResponse",
    "MEMBER-01": "MemberProfileResponse",
    "MEMBER-02": "UpdateMemberResponse",
    "ADDRESS-01": "AddressResponse",
    "ADDRESS-02": "AddressResponse",
    "ADDRESS-03": "AddressResponse",
    "ADDRESS-05": "AddressResponse",
    "NOTIFICATION-01": "NotificationSettingsResponse",
    "NOTIFICATION-02": "NotificationSettingsResponse",
    "WITHDRAWAL-01": "WithdrawalRequestResponse",
    "WITHDRAWAL-02": "WithdrawalRequestResponse",
    "FAVORITE-01": "FavoriteProductResponse",
    "FAVORITE-02": "FavoriteProductResponse",
    "CART-01": "CartResponse",
    "CART-02": "CartItemResponse",
    "CART-03": "CartItemResponse",
    "INQUIRY-01": "InquiryDetailResponse",
    "INQUIRY-02": "InquirySummaryResponse",
    "INQUIRY-03": "InquiryDetailResponse",
    "INQUIRY-04": "InquiryDetailResponse",
    "SELLER-APP-01": "SellerApplicationSummaryResponse",
    "SELLER-APP-02": "SellerApplicationSummaryResponse",
    "SELLER-APP-03": "SellerApplicationDetailResponse",
    "SELLER-APP-04": "SellerApplicationDetailResponse",
    "SELLER-APP-05": "SellerApplicationDocumentResponse",
    "SELLER-APP-07": "SellerApplicationStatusResponse",
    "SELLER-APP-08": "SellerApplicationStatusResponse",
    "SELLER-01": "SellerProfileResponse",
    "ADMIN-AUTH-01": "AdminLoginResponse",
    "ADMIN-MEMBER-01": "AdminMemberSummaryResponse",
    "ADMIN-MEMBER-02": "AdminMemberDetailResponse",
    "ADMIN-MEMBER-03": "MemberRestrictionResponse",
    "ADMIN-MEMBER-04": "MemberRestrictionResponse",
    "ADMIN-MEMBER-05": "MemberRestrictionResponse",
    "ADMIN-WITHDRAWAL-01": "AdminWithdrawalSummaryResponse",
    "ADMIN-WITHDRAWAL-02": "AdminWithdrawalDetailResponse",
    "ADMIN-WITHDRAWAL-03": "WithdrawalProcessResponse",
    "ADMIN-SELLER-APP-01": "AdminSellerApplicationSummaryResponse",
    "ADMIN-SELLER-APP-02": "AdminSellerApplicationDetailResponse",
    "ADMIN-SELLER-APP-03": "PresignedUrlResponse",
    "ADMIN-SELLER-APP-04": "StartSellerReviewResponse",
    "ADMIN-SELLER-APP-05": "ApproveSellerApplicationResponse",
    "ADMIN-SELLER-APP-06": "RejectSellerApplicationResponse",
    "ADMIN-SELLER-01": "AdminSellerSummaryResponse",
    "ADMIN-SELLER-02": "AdminSellerDetailResponse",
    "ADMIN-SELLER-03": "SellerStatusResponse",
    "ADMIN-SELLER-04": "SellerLimitsResponse",
    "ADMIN-INQUIRY-01": "AdminInquirySummaryResponse",
    "ADMIN-INQUIRY-02": "AdminInquiryDetailResponse",
    "ADMIN-INQUIRY-03": "AdminInquiryDetailResponse",
    "ADMIN-INQUIRY-04": "InquiryStatusResponse",
    "ADMIN-ROLE-01": "RoleResponse",
    "ADMIN-ROLE-02": "MemberRoleResponse",
    "ADMIN-LOG-01": "AdminActionLogSummaryResponse",
    "ADMIN-LOG-02": "AdminActionLogDetailResponse",
    "ORDER-01": "OrderSummaryResponse",
    "ORDER-02": "OrderSummaryResponse",
    "ORDER-02-1": "OrderDetailResponse",
    "ORDER-03": "OrderDetailResponse",
    "ORDER-04": "OrderDetailResponse",
    "PAY-01": "PaymentReadyResponse",
    "PAY-02": "PaymentApprovedResponse",
    "PAY-03": "PaymentFailedResponse",
    "REFUND-01": "RefundReadyResponse",
    "REFUND-02": "RefundCompletedResponse",
    "REFUND-03": "RefundSummaryResponse",
    "QUEUE-01": "QueueJoinResponse",
    "QUEUE-02": "QueueStatusResponse",
    "QUEUE-03": "PurchasePassIssueResponse",
    "QUEUE-04": "PurchasePassExpireResponse",
    "QUEUE-05": "CaptchaVerifyResponse",
    "STOCK-01": "StockReservationCreateResponse",
    "STOCK-02": "StockReservationConfirmResponse",
    "STOCK-03": "StockReservationReleaseResponse",
    "STOCK-04": "SaleSoldOutResponse",
    "PRODUCT-1": "ProductDetailResponse",
    "PRODUCT-2": "ProductDetailResponse",
    "PRODUCT-3": "ProductSummaryResponse",
    "PRODUCT-4": "ProductImageResponse",
    "PRODUCT-5": "ProductDetailResponse",
    "PRODUCT-6": "ConsoleProductSummaryResponse",
    "PRODUCT-7": "ConsoleProductSummaryResponse",
    "PRODUCT-8": "ProductActionResponse",
    "PRODUCT-9": "ProductActionResponse",
    "PRODUCT-10": "ProductActionResponse",
    "STATISTICS-1": "DropStatisticsResponse",
    "STATISTICS-2": "AuctionStatisticsResponse",
    "STATISTICS-3": "SellerStatisticsResponse",
    "STATISTICS-4": "PopularProductResponse",
    "STATISTICS-5": "RecommendedProductResponse",
    "STATISTICS-6": "RecentlyViewedProductResponse",
}

EXPLICIT_ARRAY_RESPONSES = {
    "AUTH-08",
    "ADDRESS-01",
    "FAVORITE-01",
    "INQUIRY-02",
    "SELLER-APP-02",
    "ADMIN-MEMBER-01",
    "ADMIN-MEMBER-03",
    "ADMIN-WITHDRAWAL-01",
    "ADMIN-SELLER-APP-01",
    "ADMIN-SELLER-01",
    "ADMIN-INQUIRY-01",
    "ADMIN-ROLE-01",
    "ADMIN-LOG-01",
    "ORDER-02",
    "REFUND-03",
    "PRODUCT-3",
    "PRODUCT-4",
    "PRODUCT-6",
    "PRODUCT-7",
    "PRODUCT-10",
    "STATISTICS-3",
    "STATISTICS-4",
    "STATISTICS-5",
    "STATISTICS-6",
}


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        return list(csv.DictReader(source))


def java_string(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\r", "")
        .replace("\n", "\\n")
    )


def simple_name(dto_name: str) -> str:
    return dto_name.replace("<T>", "")


def schema_location(row: dict[str, str]) -> tuple[str, str]:
    name = simple_name(row["이름"])
    if row["태그"] != "User":
        return SCHEMA_GROUPS[row["태그"]]
    for domain, names in USER_SCHEMA_GROUPS.items():
        if name in names:
            return domain, USER_SCHEMA_CLASSES[domain]
    raise ValueError(f"User DTO domain is not mapped: {name}")


def parse_fields(spec: str) -> list[dict[str, str | bool]]:
    fields = []
    for raw_line in spec.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("(") or ":" not in line:
            continue
        field_name, remainder = line.split(":", 1)
        parts = [part.strip() for part in remainder.split("|")]
        if len(parts) < 2:
            continue
        field_type = parts[0]
        required_text = parts[1]
        example = ""
        descriptions = []
        for part in parts[2:]:
            if part.startswith("예시:"):
                example = part.removeprefix("예시:").strip().strip('"')
            elif part:
                descriptions.append(part)
        fields.append(
            {
                "name": field_name.strip(),
                "type": field_type,
                "required": required_text == "필수",
                "description": " | ".join(descriptions),
                "example": example,
            }
        )
    return fields


def type_inner(type_name: str) -> tuple[str, str] | None:
    match = re.fullmatch(r"(List|Set)<\s*([^>]+)\s*>", type_name)
    return (match.group(1), match.group(2).strip()) if match else None


def dto_subpackage(name: str) -> str:
    if name.endswith("Request"):
        return "request"
    if name.endswith("Response"):
        return "response"
    if "Event" in name:
        return "event"
    # Incoming payloads (e.g. webhook payloads) belong with requests.
    return "request"


def resolve_java_type(
    type_name: str,
    dto_locations: dict[str, tuple[str, str]],
    current_domain: str,
    current_sub: str,
) -> str:
    type_name = type_name.strip()
    if type_name == "Map<String, Object>":
        return "Map<String, Object>"
    container = type_inner(type_name)
    if container:
        kind, inner = container
        resolved = resolve_java_type(inner, dto_locations, current_domain, current_sub)
        return f"{kind}<{resolved}>"
    if type_name in JAVA_SCALARS:
        return JAVA_SCALARS[type_name]
    if type_name in dto_locations:
        owner_domain, _ = dto_locations[type_name]
        owner_sub = dto_subpackage(type_name)
        if owner_domain == current_domain and owner_sub == current_sub:
            return type_name
        return f"com.c203.limit.{owner_domain}.dto.{owner_sub}.{type_name}"
    # Enum definitions were not included in the export. Keep them open as strings.
    return "String"


DTO_IMPORT_RULES = [
    ("BigDecimal", "import java.math.BigDecimal;"),
    ("Instant", "import java.time.Instant;"),
    ("LocalDate", "import java.time.LocalDate;"),
    ("OffsetDateTime", "import java.time.OffsetDateTime;"),
    ("List<", "import java.util.List;"),
    ("Map<", "import java.util.Map;"),
    ("Set<", "import java.util.Set;"),
]


def dto_class_file(
    domain: str,
    row: dict[str, str],
    dto_locations: dict[str, tuple[str, str]],
) -> tuple[str, str, str]:
    """Return (class name, subpackage, source) for one DTO as a plain Java class file."""
    name = simple_name(row["이름"])
    sub = dto_subpackage(name)
    generic = "<T>" if row["이름"].endswith("<T>") else ""
    fields = []
    for field in parse_fields(row["DTO 명세"]):
        required = "REQUIRED" if field["required"] else "NOT_REQUIRED"
        annotation_parts = [f'description = "{java_string(str(field["description"]))}"']
        if field["example"]:
            annotation_parts.append(f'example = "{java_string(str(field["example"]))}"')
        annotation_parts.append(f"requiredMode = Schema.RequiredMode.{required}")
        annotation = "@Schema(" + ", ".join(annotation_parts) + ")"
        java_type = resolve_java_type(str(field["type"]), dto_locations, domain, sub)
        fields.append((annotation, java_type, str(field["name"])))

    description = java_string(row["설명"] or row["비고"] or name)
    class_annotation = f'@Schema(name = "{name}", description = "{description}")'
    if fields:
        field_section = "\n\n".join(
            f"    {annotation}\n    private final {java_type} {field_name};"
            for annotation, java_type, field_name in fields
        )
        body = (
            "@Getter\n@RequiredArgsConstructor\n"
            f"{class_annotation}\npublic class {name}{generic} {{\n\n{field_section}\n}}"
        )
    else:
        body = f"{class_annotation}\npublic class {name}{generic} {{}}"

    java_imports = [statement for token, statement in DTO_IMPORT_RULES if token in body]
    sections = [f"package com.c203.limit.{domain}.dto.{sub};", ""]
    if java_imports:
        sections.extend(java_imports)
        sections.append("")
    sections.append("import io.swagger.v3.oas.annotations.media.Schema;")
    if fields:
        sections.append("import lombok.Getter;")
        sections.append("import lombok.RequiredArgsConstructor;")
    sections.append("")
    sections.append("/** Generated from the exported DTO specification. */")
    sections.append(body)
    return name, sub, "\n".join(sections) + "\n"


def extract_json(text: str):
    decoder = json.JSONDecoder()
    for match in re.finditer(r"\{", text):
        try:
            value, _ = decoder.raw_decode(text[match.start() :])
            return value
        except json.JSONDecodeError:
            continue
    return None


def payload_keys(value, response: bool = False) -> tuple[set[str], bool]:
    if value is None:
        return set(), False
    if response and isinstance(value, dict) and "data" in value:
        value = value["data"]
    is_array = isinstance(value, list)
    if is_array:
        value = value[0] if value else {}
    if isinstance(value, dict) and "content" in value and isinstance(value["content"], list):
        is_array = True
        value = value["content"][0] if value["content"] else {}
    return (set(value) if isinstance(value, dict) else set()), is_array


def match_dto(
    text: str,
    category: str,
    dto_rows: list[dict[str, str]],
    dto_fields: dict[str, set[str]],
    response: bool = False,
) -> tuple[str | None, bool]:
    candidates = [row for row in dto_rows if row["API분류"].startswith(category)]
    for row in candidates:
        name = simple_name(row["이름"])
        if re.search(rf"(?<![A-Za-z0-9]){re.escape(name)}(?![A-Za-z0-9])", text):
            _, is_array = payload_keys(extract_json(text), response=response)
            return name, is_array
    keys, is_array = payload_keys(extract_json(text), response=response)
    if not keys:
        return None, is_array
    ranked = []
    for row in candidates:
        name = simple_name(row["이름"])
        fields = dto_fields.get(name, set())
        overlap = len(keys & fields)
        if overlap:
            ranked.append((overlap / max(len(keys), 1), overlap, -abs(len(fields) - len(keys)), name))
    if not ranked:
        return None, is_array
    best = max(ranked)
    return (best[3], is_array) if best[1] >= 1 and best[0] >= 0.4 else (None, is_array)


def api_code(feature: str) -> str:
    return feature.split(maxsplit=1)[0]


def api_group(code: str) -> str:
    return code.split("-")[0]


def method_name(code: str) -> str:
    return re.sub(r"[^A-Za-z0-9]", "", code).lower()


def restful_route(code: str, api: dict[str, str]) -> tuple[str, str]:
    return RESTFUL_ROUTES.get(
        code,
        (api["HTTP 메서드"].upper(), api["API Path"].split("?", 1)[0]),
    )


def parameter_type(name: str) -> str:
    lowered = name.lower()
    if lowered in {"page", "size", "limit", "quantity", "batchsize"}:
        return "Integer"
    if lowered.endswith("id"):
        return "Long"
    if lowered.startswith("is") or lowered.startswith("has") or lowered == "includedeleted":
        return "Boolean"
    if "price" in lowered or "amount" in lowered:
        return "BigDecimal"
    if lowered in {"from", "to", "date", "createdfrom", "createdto"}:
        return "LocalDate"
    return "String"


def collect_parameters(api: dict[str, str]) -> list[tuple[str, str, str, bool]]:
    path_with_query = api["API Path"]
    path = path_with_query.split("?", 1)[0]
    parameters = []
    seen = set()
    for name in re.findall(r"\{([A-Za-z][A-Za-z0-9]*)\}", path):
        parameters.append(("path", name, parameter_type(name), True))
        seen.add(name.lower())
    if "?" in path_with_query:
        query = path_with_query.split("?", 1)[1]
        for name in re.findall(r"(?:^|&)([A-Za-z][A-Za-z0-9]*)=", query):
            if name.lower() not in seen:
                parameters.append(("query", name, parameter_type(name), False))
                seen.add(name.lower())
    query_match = re.search(r"Query:\s*([^\n]+)", api["Request"])
    if query_match:
        for name in re.findall(r"([A-Za-z][A-Za-z0-9]*)\s*\(", query_match.group(1)):
            if name.lower() not in seen:
                parameters.append(("query", name, parameter_type(name), False))
                seen.add(name.lower())
    if "Idempotency-Key" in api["Request"]:
        parameters.append(("header", "idempotencyKey", "String", True))
    return parameters


def api_interface(
    domain: str,
    interface_name: str,
    tag_name: str,
    operations: list[dict],
    dto_locations: dict[str, tuple[str, str]],
) -> tuple[str, list[tuple[str, list[tuple[str, str]]]]]:
    methods = []
    method_signatures: list[tuple[str, list[tuple[str, str]]]] = []
    for operation in operations:
        api = operation["api"]
        code = operation["code"]
        http_method = api["HTTP 메서드"].upper()
        path = api["API Path"].split("?", 1)[0]
        summary = operation["summary"]
        description = "요청\n" + api["Request"] + "\n\n응답\n" + api["Response"]
        request_schema = operation["request_schema"]
        response_schema = operation["response_schema"]
        response_is_array = operation["response_is_array"]
        public = "권한: PUBLIC" in api["Request"]
        internal = path.startswith("/api/v1/internal/")
        security = ""
        if not public:
            scheme = "internalApiKey" if internal else "bearerAuth"
            security = f', security = @SecurityRequirement(name = "{scheme}")'

        annotations = [
            f'    @Operation(operationId = "{method_name(code)}", summary = "{java_string(summary)}", '
            f'description = "{java_string(description)}"{security})'
        ]
        responses = []
        success_codes = list(dict.fromkeys(re.findall(r"\b(200|201|204)\b", api["Response"]))) or ["200"]
        for response_code in success_codes[:1]:
            if response_code == "204" or not response_schema:
                content = ""
            elif response_is_array:
                content = (
                    ', content = @Content(array = @ArraySchema(schema = '
                    f'@Schema(ref = "#/components/schemas/{response_schema}")))'
                )
            else:
                content = (
                    ', content = @Content(schema = '
                    f'@Schema(ref = "#/components/schemas/{response_schema}"))'
                )
            responses.append(
                f'        @ApiResponse(responseCode = "{response_code}", description = "명세 응답"{content})'
            )
        for error_code in list(dict.fromkeys(re.findall(r"\b(400|401|403|404|409|422|429)\b", api["Response"])))[:4]:
            responses.append(f'        @ApiResponse(responseCode = "{error_code}", description = "명세 오류 응답")')
        annotations.append("    @ApiResponses({\n" + ",\n".join(responses) + "\n    })")

        consumes = ""
        multipart = "multipart/form-data" in api["Request"]
        has_body = (
            request_schema is not None
            or "Body:" in api["Request"]
            or "Body(" in api["Request"]
            or "request(JSON)" in api["Request"]
        )
        if multipart:
            consumes = ", consumes = MediaType.MULTIPART_FORM_DATA_VALUE"
        annotations.append(
            f'    @RequestMapping(method = RequestMethod.{http_method}, path = "{path}", '
            f"produces = MediaType.APPLICATION_JSON_VALUE{consumes})"
        )

        parameters = []
        simple_params: list[tuple[str, str]] = []
        for source, name, java_type, required in collect_parameters(api):
            simple_params.append((java_type, name))
            if source == "path":
                parameters.append(f'@PathVariable("{name}") {java_type} {name}')
            elif source == "header":
                parameters.append(
                    f'@RequestHeader(name = "Idempotency-Key", required = true) {java_type} {name}'
                )
            else:
                parameters.append(
                    f'@RequestParam(name = "{name}", required = {str(required).lower()}) {java_type} {name}'
                )
        if has_body:
            schema = (
                f'@Schema(ref = "#/components/schemas/{request_schema}")'
                if request_schema
                else '@Schema(type = "object")'
            )
            openapi_body = (
                "@io.swagger.v3.oas.annotations.parameters.RequestBody("
                f"required = false, content = @Content(schema = {schema}))"
            )
            if multipart:
                parameters.append(f'{openapi_body} @RequestPart(name = "request", required = false) Object body')
            else:
                parameters.append(
                    f"{openapi_body} @org.springframework.web.bind.annotation.RequestBody(required = false) Object body"
                )
            simple_params.append(("Object", "body"))
        if multipart:
            parameters.append('@RequestPart(name = "files", required = false) MultipartFile[] files')
            simple_params.append(("MultipartFile[]", "files"))
        parameter_block = ""
        if parameters:
            parameter_block = "\n            " + ",\n            ".join(parameters) + "\n    "
        methods.append(
            "\n".join(annotations)
            + f"\n    ResponseEntity<Void> {method_name(code)}({parameter_block});"
        )
        method_signatures.append((method_name(code), simple_params))

    return f"""package com.c203.limit.{domain}.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "{tag_name}")
public interface {interface_name} {{

{chr(10).join(methods)}
}}
""", method_signatures


def domain_controller(
    domain: str,
    interface_name: str,
    method_signatures: list[tuple[str, list[tuple[str, str]]]],
) -> str:
    class_name = interface_name.removesuffix("Api") + "Controller"
    used_types = {java_type for _, params in method_signatures for java_type, _ in params}
    imports = []
    if "BigDecimal" in used_types:
        imports.append("import java.math.BigDecimal;")
    if "LocalDate" in used_types:
        imports.append("import java.time.LocalDate;")
    if imports:
        imports.append("")
    imports.append("import org.springframework.http.ResponseEntity;")
    imports.append("import org.springframework.web.bind.annotation.RestController;")
    if "MultipartFile[]" in used_types:
        imports.append("import org.springframework.web.multipart.MultipartFile;")
    methods = []
    for name, params in method_signatures:
        arglist = ", ".join(f"{java_type} {param_name}" for java_type, param_name in params)
        methods.append(
            "    @Override\n"
            f"    public ResponseEntity<Void> {name}({arglist}) {{\n"
            "        return null;\n"
            "    }"
        )
    return (
        f"package com.c203.limit.{domain}.controller;\n\n"
        + "\n".join(imports)
        + "\n\n/** Domain controller. Unimplemented methods return null until real implementations are added. */\n"
        + "@RestController\n"
        + f"public class {class_name} implements {interface_name} {{\n\n"
        + "\n\n".join(methods)
        + "\n}\n"
    )


def schema_config(schema_classes: list[tuple[str, str, str]]) -> str:
    class_refs = ",\n            ".join([
        "com.c203.limit.global.response.ApiResponse.class",
        "com.c203.limit.global.response.ApiErrorResponse.class",
        *(
            f"com.c203.limit.{domain}.dto.{sub}.{name}.class"
            for domain, sub, name in schema_classes
        ),
    ])
    return f"""package com.c203.limit.swagger.config;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {{

    private static final List<Class<?>> SCHEMA_TYPES = List.of(
            {class_refs}
    );

    @Bean
    OpenAPI limitOpenApi() {{
        Components components = new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes("internalApiKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Internal-Api-Key"));

        for (Class<?> schemaType : SCHEMA_TYPES) {{
            Map<String, Schema> schemas = ModelConverters.getInstance().readAll(schemaType);
            schemas.forEach(components::addSchemas);
        }}

        return new OpenAPI()
                .info(new Info()
                        .title("Limit API")
                        .version("v1")
                        .description("DTO/API 명세 기반 계약 초안입니다. 미구현 API는 빈 응답을 반환합니다."))
                .components(components);
    }}
}}
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dto-csv", required=True, type=Path)
    parser.add_argument("--api-csv", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=Path("backend/src/main/java/com/c203/limit"))
    args = parser.parse_args()

    dto_rows_raw = read_csv(args.dto_csv)
    api_rows = read_csv(args.api_csv)

    dto_rows = []
    seen_dtos = set()
    for row in dto_rows_raw:
        name = simple_name(row["이름"])
        if name not in seen_dtos and name not in GLOBAL_RESPONSE_DTOS:
            seen_dtos.add(name)
            dto_rows.append(row)

    dto_locations = {
        simple_name(row["이름"]): schema_location(row)
        for row in dto_rows
    }
    dto_fields = {
        simple_name(row["이름"]): {str(field["name"]) for field in parse_fields(row["DTO 명세"])}
        for row in dto_rows
    }

    config_dir = args.output / "swagger" / "config"
    config_dir.mkdir(parents=True, exist_ok=True)

    grouped_dtos = defaultdict(list)
    for row in dto_rows:
        domain, _ = schema_location(row)
        grouped_dtos[domain].append(row)
    schema_classes = []
    for domain, rows in sorted(grouped_dtos.items()):
        for row in rows:
            name, sub, source = dto_class_file(domain, row, dto_locations)
            dto_dir = args.output / domain / "dto" / sub
            dto_dir.mkdir(parents=True, exist_ok=True)
            (dto_dir / f"{name}.java").write_text(source, encoding="utf-8")
            schema_classes.append((domain, sub, name))

    # OpenAPI cannot contain two operations with the same HTTP method and path.
    merged = {}
    for api in api_rows:
        code = api_code(api["기능"])
        http_method, path = restful_route(code, api)
        normalized_api = dict(api)
        normalized_api["HTTP 메서드"] = http_method
        normalized_api["API Path"] = path
        key = (http_method, path)
        if key in merged:
            merged[key]["summary"] += " / " + api["기능"].split(maxsplit=1)[1]
            merged[key]["api"]["Request"] += "\n\n" + normalized_api["Request"]
            merged[key]["api"]["Response"] += "\n\n" + normalized_api["Response"]
            continue
        request_schema, _ = match_dto(api["Request"], "API Request", dto_rows, dto_fields)
        response_schema, response_is_array = match_dto(
            api["Response"], "API Response", dto_rows, dto_fields, response=True
        )
        request_schema = EXPLICIT_REQUESTS.get(code, request_schema)
        response_schema = EXPLICIT_RESPONSES.get(code, response_schema)
        response_is_array = code in EXPLICIT_ARRAY_RESPONSES or response_is_array
        merged[key] = {
            "api": normalized_api,
            "code": code,
            "summary": api["기능"].split(maxsplit=1)[1],
            "request_schema": request_schema,
            "response_schema": response_schema,
            "response_is_array": response_is_array,
        }

    grouped_apis = defaultdict(list)
    for operation in merged.values():
        grouped_apis[api_group(operation["code"])].append(operation)
    for group, operations in sorted(grouped_apis.items()):
        domain, interface_name, tag_name = API_GROUPS[group]
        controller_dir = args.output / domain / "controller"
        controller_dir.mkdir(parents=True, exist_ok=True)
        for layer in ("service", "domain", "repository"):
            layer_dir = args.output / domain / layer
            layer_dir.mkdir(parents=True, exist_ok=True)
            if not any(layer_dir.iterdir()):
                (layer_dir / ".gitkeep").touch()
        interface_source, method_signatures = api_interface(
            domain, interface_name, tag_name, operations, dto_locations
        )
        (controller_dir / f"{interface_name}.java").write_text(
            interface_source, encoding="utf-8"
        )
        class_name = interface_name.removesuffix("Api") + "Controller"
        controller_path = controller_dir / f"{class_name}.java"
        # Never overwrite an existing controller: it may contain real implementations.
        if not controller_path.exists():
            controller_path.write_text(
                domain_controller(domain, interface_name, method_signatures), encoding="utf-8"
            )
        else:
            print(f"skip existing controller: {controller_path}")

    (config_dir / "OpenApiConfig.java").write_text(schema_config(schema_classes), encoding="utf-8")
    print(f"generated {len(dto_rows)} schemas and {len(merged)} operations")


if __name__ == "__main__":
    main()
