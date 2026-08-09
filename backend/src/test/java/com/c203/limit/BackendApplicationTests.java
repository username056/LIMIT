package com.c203.limit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ChatOutboxEventRepository;
import com.c203.limit.domain.chat.repository.ReinspectionRequestMessageRepository;
import com.c203.limit.domain.chat.repository.ListingChatReader;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.member.repository.MemberTermsAgreementRepository;
import com.c203.limit.domain.admin.repository.AdminAccountRepository;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.admin.repository.MemberRestrictionRepository;
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.domain.inspection.repository.ReinspectionRequestItemRepository;
import com.c203.limit.domain.inspection.repository.ReinspectionRequestRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.WishlistRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.MediaUploadSessionRepository;
import com.c203.limit.domain.product.service.ProductApplicationService;
import com.c203.limit.domain.product.service.ProductCatalogService;
import com.c203.limit.domain.payment.service.PaymentService;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
})
class BackendApplicationTests {

    @MockitoBean
    com.c203.limit.domain.rtc.service.RtcCallService rtcCallService;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    MemberRepository memberRepository;

    @MockitoBean
    MemberTermsAgreementRepository memberTermsAgreementRepository;

    @MockitoBean
    SocialAccountRepository socialAccountRepository;

    @MockitoBean
    AdminAccountRepository adminAccountRepository;

    @MockitoBean
    AdminActionLogRepository adminActionLogRepository;

    @MockitoBean
    MemberRestrictionRepository memberRestrictionRepository;

    @MockitoBean
    ChatRoomRepository chatRoomRepository;

    @MockitoBean
    ChatRoomParticipantRepository chatRoomParticipantRepository;

    @MockitoBean
    ChatMessageRepository chatMessageRepository;

    @MockitoBean
    ChatOutboxEventRepository chatOutboxEventRepository;

    @MockitoBean
    ReinspectionRequestMessageRepository reinspectionRequestMessageRepository;

    @MockitoBean
    com.c203.limit.domain.chat.repository.ChatMediaRepository chatMediaRepository;

    @MockitoBean
    com.c203.limit.domain.chat.repository.ChatMessageMediaRepository chatMessageMediaRepository;

    @MockitoBean
    com.c203.limit.domain.chat.repository.ChatRoomContextReader chatRoomContextReader;

    @MockitoBean
    ListingChatReader listingChatReader;

    @MockitoBean
    com.c203.limit.domain.payment.repository.ExpiredReservationCandidateReader
            expiredReservationCandidateReader;

    @MockitoBean
    ListingRepository listingRepository;

    @MockitoBean
    com.c203.limit.domain.product.repository.DeviceModelRepository deviceModelRepository;

    @MockitoBean
    com.c203.limit.domain.product.repository.DeviceVariantRepository deviceVariantRepository;

    @MockitoBean
    com.c203.limit.domain.product.repository.DeviceCategoryRepository deviceCategoryRepository;

    @MockitoBean
    com.c203.limit.domain.product.repository.ManufacturerRepository manufacturerRepository;

    @MockitoBean
    WishlistRepository wishlistRepository;

    // JdbcClient를 쓰는 리더는 DataSource 자동설정을 끈 이 컨텍스트에서 만들 수 없다.
    // 위 chatRoomContextReader와 같은 이유로 대체 빈을 둔다.
    @MockitoBean
    com.c203.limit.domain.product.repository.ProductEngagementReader productEngagementReader;

    @MockitoBean
    ProductApplicationService productApplicationService;

    @MockitoBean
    com.c203.limit.domain.product.moderation.service.ListingModerationService
            listingModerationService;

    @MockitoBean
    com.c203.limit.domain.product.moderation.service.ModerationRiskService moderationRiskService;

    @MockitoBean
    ProductCatalogService productCatalogService;

    @MockitoBean
    com.c203.limit.domain.inspection.checklist.ChecklistGenerationService
            checklistGenerationService;

    @MockitoBean
    ListingStatusHistoryRepository listingStatusHistoryRepository;

    @MockitoBean
    ListingImageRepository listingImageRepository;

    @MockitoBean
    MediaUploadSessionRepository mediaUploadSessionRepository;

    @MockitoBean
    PaymentService paymentService;

    @MockitoBean
    com.c203.limit.domain.payment.service.PaymentReservationExpirationService
            paymentReservationExpirationService;

    @MockitoBean
    com.c203.limit.domain.payment.repository.ListingOrderSummaryReader listingOrderSummaryReader;

    @MockitoBean
    com.c203.limit.domain.payment.repository.PaymentRepository paymentRepository;

    @MockitoBean
    com.c203.limit.domain.seller.repository.SellerRepository sellerRepository;

    @MockitoBean
    EvidenceRepository evidenceRepository;

    @MockitoBean
    OcrResultRepository ocrResultRepository;

    @MockitoBean
    ListingOwnerReader listingOwnerReader;

    @MockitoBean
    ListingChecklistItemRepository listingChecklistItemRepository;

    @MockitoBean
    com.c203.limit.domain.inspection.agent.InspectionSessionRepository inspectionSessionRepository;

    @MockitoBean
    com.c203.limit.domain.inspection.agent.InspectionSessionTestResultRepository
            inspectionSessionTestResultRepository;

    @MockitoBean
    ReinspectionRequestRepository reinspectionRequestRepository;

    @MockitoBean
    ReinspectionRequestItemRepository reinspectionRequestItemRepository;

    @MockitoBean
    DxdiagResultRepository dxdiagResultRepository;

    @MockitoBean
    BatteryReportResultRepository batteryReportResultRepository;

    @MockitoBean
    com.c203.limit.domain.inspection.service.ModelChecklistResearchService
            modelChecklistResearchService;

    @MockitoBean
    com.c203.limit.domain.product.service.DeviceModelRequestService deviceModelRequestService;

    @MockitoBean
    com.c203.limit.domain.product.service.DeviceModelManagementService deviceModelManagementService;

    @Test
    void contextLoads() {
    }
}
