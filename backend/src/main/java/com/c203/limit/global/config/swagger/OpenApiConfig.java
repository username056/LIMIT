package com.c203.limit.global.config.swagger;

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
public class OpenApiConfig {

    private static final List<Class<?>> SCHEMA_TYPES = List.of(
            com.c203.limit.domain.address.dto.request.CreateAddressRequest.class,
            com.c203.limit.domain.address.dto.request.UpdateAddressRequest.class,
            com.c203.limit.domain.address.dto.response.AddressResponse.class,
            com.c203.limit.domain.admin.dto.request.AdminLoginRequest.class,
            com.c203.limit.domain.admin.dto.response.AdminLoginResponse.class,
            com.c203.limit.domain.admin.dto.response.AdminSummaryResponse.class,
            com.c203.limit.domain.admin.dto.request.AdminMemberSearchRequest.class,
            com.c203.limit.domain.admin.dto.response.AdminMemberSummaryResponse.class,
            com.c203.limit.domain.admin.dto.response.AdminMemberDetailResponse.class,
            com.c203.limit.domain.admin.dto.request.MemberRestrictionSearchRequest.class,
            com.c203.limit.domain.admin.dto.request.CreateMemberRestrictionRequest.class,
            com.c203.limit.domain.admin.dto.request.ReleaseMemberRestrictionRequest.class,
            com.c203.limit.domain.admin.dto.response.MemberRestrictionResponse.class,
            com.c203.limit.domain.admin.dto.request.AdminWithdrawalSearchRequest.class,
            com.c203.limit.domain.admin.dto.response.AdminWithdrawalSummaryResponse.class,
            com.c203.limit.domain.admin.dto.response.WithdrawalBlockingResourcesResponse.class,
            com.c203.limit.domain.admin.dto.response.AdminWithdrawalDetailResponse.class,
            com.c203.limit.domain.admin.dto.request.ProcessWithdrawalRequest.class,
            com.c203.limit.domain.admin.dto.response.WithdrawalProcessResponse.class,
            com.c203.limit.domain.admin.dto.request.AdminSellerApplicationSearchRequest.class,
            com.c203.limit.domain.admin.dto.response.AdminSellerApplicationSummaryResponse.class,
            com.c203.limit.domain.admin.dto.response.AdminSellerApplicationDetailResponse.class,
            com.c203.limit.domain.admin.dto.response.PresignedUrlResponse.class,
            com.c203.limit.domain.admin.dto.response.StartSellerReviewResponse.class,
            com.c203.limit.domain.admin.dto.request.ApproveSellerApplicationRequest.class,
            com.c203.limit.domain.admin.dto.response.ApproveSellerApplicationResponse.class,
            com.c203.limit.domain.admin.dto.request.RejectSellerApplicationRequest.class,
            com.c203.limit.domain.admin.dto.response.RejectSellerApplicationResponse.class,
            com.c203.limit.domain.admin.dto.request.AdminSellerSearchRequest.class,
            com.c203.limit.domain.admin.dto.response.AdminSellerSummaryResponse.class,
            com.c203.limit.domain.admin.dto.response.AdminSellerDetailResponse.class,
            com.c203.limit.domain.admin.dto.request.UpdateSellerStatusRequest.class,
            com.c203.limit.domain.admin.dto.response.SellerStatusResponse.class,
            com.c203.limit.domain.admin.dto.request.UpdateSellerLimitsRequest.class,
            com.c203.limit.domain.admin.dto.response.SellerLimitsResponse.class,
            com.c203.limit.domain.admin.dto.request.AdminInquirySearchRequest.class,
            com.c203.limit.domain.admin.dto.response.AdminInquirySummaryResponse.class,
            com.c203.limit.domain.admin.dto.response.AdminInquiryDetailResponse.class,
            com.c203.limit.domain.admin.dto.request.UpsertInquiryAnswerRequest.class,
            com.c203.limit.domain.admin.dto.request.UpdateInquiryStatusRequest.class,
            com.c203.limit.domain.admin.dto.response.InquiryStatusResponse.class,
            com.c203.limit.domain.admin.dto.response.RoleResponse.class,
            com.c203.limit.domain.admin.dto.request.GrantRoleRequest.class,
            com.c203.limit.domain.admin.dto.request.RevokeRoleRequest.class,
            com.c203.limit.domain.admin.dto.response.MemberRoleResponse.class,
            com.c203.limit.domain.admin.dto.request.AdminActionLogSearchRequest.class,
            com.c203.limit.domain.admin.dto.response.AdminActionLogSummaryResponse.class,
            com.c203.limit.domain.admin.dto.response.AdminActionLogDetailResponse.class,
            com.c203.limit.domain.auth.dto.request.SignupRequest.class,
            com.c203.limit.domain.auth.dto.response.SignupResponse.class,
            com.c203.limit.domain.auth.dto.request.LoginRequest.class,
            com.c203.limit.domain.auth.dto.response.LoginResponse.class,
            com.c203.limit.domain.auth.dto.request.TokenRefreshRequest.class,
            com.c203.limit.domain.auth.dto.response.TokenResponse.class,
            com.c203.limit.domain.auth.dto.request.LogoutRequest.class,
            com.c203.limit.domain.auth.dto.response.EmailAvailabilityResponse.class,
            com.c203.limit.domain.auth.dto.response.NicknameAvailabilityResponse.class,
            com.c203.limit.domain.auth.dto.request.SocialLoginRequest.class,
            com.c203.limit.domain.auth.dto.response.SocialLoginResponse.class,
            com.c203.limit.domain.auth.dto.response.SocialAccountResponse.class,
            com.c203.limit.domain.cart.dto.request.AddCartItemRequest.class,
            com.c203.limit.domain.cart.dto.request.UpdateCartItemQuantityRequest.class,
            com.c203.limit.domain.cart.dto.response.CartItemResponse.class,
            com.c203.limit.domain.cart.dto.response.CartResponse.class,
            com.c203.limit.global.response.ApiResponse.class,
            com.c203.limit.global.response.PageResponse.class,
            com.c203.limit.global.response.ApiErrorResponse.class,
            com.c203.limit.domain.favorite.dto.response.FavoriteProductResponse.class,
            com.c203.limit.domain.inquiry.dto.request.CreateInquiryRequest.class,
            com.c203.limit.domain.inquiry.dto.request.UpdateInquiryRequest.class,
            com.c203.limit.domain.inquiry.dto.response.InquirySummaryResponse.class,
            com.c203.limit.domain.inquiry.dto.response.InquiryAnswerResponse.class,
            com.c203.limit.domain.inquiry.dto.response.InquiryDetailResponse.class,
            com.c203.limit.domain.member.dto.response.MemberSummaryResponse.class,
            com.c203.limit.domain.member.dto.response.MemberProfileResponse.class,
            com.c203.limit.domain.member.dto.request.UpdateMemberRequest.class,
            com.c203.limit.domain.member.dto.response.UpdateMemberResponse.class,
            com.c203.limit.domain.member.dto.request.ChangePasswordRequest.class,
            com.c203.limit.domain.notification.dto.response.NotificationSettingsResponse.class,
            com.c203.limit.domain.notification.dto.request.UpdateNotificationSettingsRequest.class,
            com.c203.limit.domain.order.dto.request.CreateOrderRequest.class,
            com.c203.limit.domain.order.dto.response.OrderSummaryResponse.class,
            com.c203.limit.domain.order.dto.response.OrderDetailResponse.class,
            com.c203.limit.domain.order.dto.request.CancelOrderRequest.class,
            com.c203.limit.domain.order.dto.request.ConfirmOrderRequest.class,
            com.c203.limit.domain.order.dto.event.OrderStatusChangedEventV1.class,
            com.c203.limit.domain.payment.dto.request.CreatePaymentRequest.class,
            com.c203.limit.domain.payment.dto.response.PaymentReadyResponse.class,
            com.c203.limit.domain.payment.dto.request.TossWebhookPayload.class,
            com.c203.limit.domain.payment.dto.response.PaymentApprovedResponse.class,
            com.c203.limit.domain.payment.dto.request.FailPaymentRequest.class,
            com.c203.limit.domain.payment.dto.response.PaymentFailedResponse.class,
            com.c203.limit.domain.payment.dto.event.PaymentCompletedEventV1.class,
            com.c203.limit.domain.payment.dto.event.PaymentFailedEventV1.class,
            com.c203.limit.domain.product.dto.request.CreateProductRequest.class,
            com.c203.limit.domain.product.dto.request.UpdateProductRequest.class,
            com.c203.limit.domain.product.dto.request.CreateProductActionRequest.class,
            com.c203.limit.domain.product.dto.response.ProductDetailResponse.class,
            com.c203.limit.domain.product.dto.response.ProductSummaryResponse.class,
            com.c203.limit.domain.product.dto.response.ConsoleProductSummaryResponse.class,
            com.c203.limit.domain.product.dto.response.ProductImageResponse.class,
            com.c203.limit.domain.product.dto.response.ProductAuthenticityProofResponse.class,
            com.c203.limit.domain.product.dto.response.InventorySummaryResponse.class,
            com.c203.limit.domain.product.dto.response.ProductActionResponse.class,
            com.c203.limit.domain.queue.dto.request.PurchasePassIssueRequest.class,
            com.c203.limit.domain.queue.dto.response.PurchasePassIssueResponse.class,
            com.c203.limit.domain.queue.dto.request.PurchasePassExpireRequest.class,
            com.c203.limit.domain.queue.dto.response.PurchasePassExpireResponse.class,
            com.c203.limit.domain.queue.dto.request.QueueJoinRequest.class,
            com.c203.limit.domain.queue.dto.response.QueueJoinResponse.class,
            com.c203.limit.domain.queue.dto.response.QueueStatusResponse.class,
            com.c203.limit.domain.queue.dto.response.PurchasePassSummaryResponse.class,
            com.c203.limit.domain.queue.dto.request.CaptchaVerifyRequest.class,
            com.c203.limit.domain.queue.dto.response.CaptchaVerifyResponse.class,
            com.c203.limit.domain.refund.dto.request.CreateRefundRequest.class,
            com.c203.limit.domain.refund.dto.response.RefundReadyResponse.class,
            com.c203.limit.domain.refund.dto.request.CompleteRefundRequest.class,
            com.c203.limit.domain.refund.dto.response.RefundCompletedResponse.class,
            com.c203.limit.domain.refund.dto.response.RefundSummaryResponse.class,
            com.c203.limit.domain.refund.dto.event.RefundCompletedEventV1.class,
            com.c203.limit.domain.seller.dto.request.CreateSellerApplicationRequest.class,
            com.c203.limit.domain.seller.dto.request.UpdateSellerApplicationRequest.class,
            com.c203.limit.domain.seller.dto.request.CancelSellerApplicationRequest.class,
            com.c203.limit.domain.seller.dto.response.SellerApplicationDocumentResponse.class,
            com.c203.limit.domain.seller.dto.response.SellerApplicationSummaryResponse.class,
            com.c203.limit.domain.seller.dto.response.SellerApplicationDetailResponse.class,
            com.c203.limit.domain.seller.dto.response.SellerApplicationStatusResponse.class,
            com.c203.limit.domain.seller.dto.response.SellerProfileResponse.class,
            com.c203.limit.domain.statistics.dto.response.DropStatisticsResponse.class,
            com.c203.limit.domain.statistics.dto.response.AuctionStatisticsResponse.class,
            com.c203.limit.domain.statistics.dto.response.SellerStatisticsResponse.class,
            com.c203.limit.domain.statistics.dto.response.PopularProductResponse.class,
            com.c203.limit.domain.statistics.dto.response.RecommendedProductResponse.class,
            com.c203.limit.domain.statistics.dto.response.RecentlyViewedProductResponse.class,
            com.c203.limit.domain.stock.dto.request.StockReservationCreateRequest.class,
            com.c203.limit.domain.stock.dto.response.StockReservationCreateResponse.class,
            com.c203.limit.domain.stock.dto.request.StockReservationConfirmRequest.class,
            com.c203.limit.domain.stock.dto.response.StockReservationConfirmResponse.class,
            com.c203.limit.domain.stock.dto.request.StockReservationReleaseRequest.class,
            com.c203.limit.domain.stock.dto.response.StockReservationReleaseResponse.class,
            com.c203.limit.domain.stock.dto.request.SaleSoldOutRequest.class,
            com.c203.limit.domain.stock.dto.response.SaleSoldOutResponse.class,
            com.c203.limit.domain.withdrawal.dto.request.CreateWithdrawalRequest.class,
            com.c203.limit.domain.withdrawal.dto.response.WithdrawalRequestResponse.class
    );

    @Bean
    OpenAPI limitOpenApi() {
        Components components = new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes("internalApiKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Internal-Api-Key"));

        for (Class<?> schemaType : SCHEMA_TYPES) {
            Map<String, Schema> schemas = ModelConverters.getInstance().readAll(schemaType);
            schemas.forEach(components::addSchemas);
        }

        return new OpenAPI()
                .info(new Info()
                        .title("Limit API")
                        .version("v1")
                        .description("DTO/API 명세 기반 계약 초안입니다. 미구현 API는 빈 응답을 반환합니다."))
                .components(components);
    }
}
