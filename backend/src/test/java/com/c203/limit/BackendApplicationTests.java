package com.c203.limit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
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
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.WishlistRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
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
    WishlistRepository wishlistRepository;

    @MockitoBean
    ProductApplicationService productApplicationService;

    @MockitoBean
    ProductCatalogService productCatalogService;

    @MockitoBean
    com.c203.limit.domain.inspection.checklist.ChecklistGenerationService
            checklistGenerationService;

    @MockitoBean
    ListingStatusHistoryRepository listingStatusHistoryRepository;

    @MockitoBean
    PaymentService paymentService;

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
    DxdiagResultRepository dxdiagResultRepository;

    @MockitoBean
    BatteryReportResultRepository batteryReportResultRepository;

    @Test
    void contextLoads() {
    }
}
