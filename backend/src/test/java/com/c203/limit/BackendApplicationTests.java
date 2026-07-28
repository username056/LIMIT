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
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.WishlistRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.domain.product.service.ProductApplicationService;
import com.c203.limit.domain.product.service.ProductCatalogService;

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
    ListingRepository listingRepository;

    @MockitoBean
    WishlistRepository wishlistRepository;

    @MockitoBean
    ProductApplicationService productApplicationService;

    @MockitoBean
    ProductCatalogService productCatalogService;

    @MockitoBean
    ListingStatusHistoryRepository listingStatusHistoryRepository;

    @MockitoBean
    com.c203.limit.domain.seller.repository.SellerRepository sellerRepository;

    @Test
    void contextLoads() {
    }
}
