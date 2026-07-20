package com.c203.limit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.seller.repository.SellerApplicationRepository;
import com.c203.limit.domain.seller.repository.SellerApplicationDocumentRepository;
import com.c203.limit.domain.seller.repository.SellerRepository;
import com.c203.limit.domain.admin.repository.*;
import com.c203.limit.domain.inquiry.repository.*;
import com.c203.limit.domain.withdrawal.repository.WithdrawalRequestRepository;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
})
class BackendApplicationTests {

    @MockitoBean
    MemberRepository memberRepository;
    @MockitoBean
    SocialAccountRepository socialAccountRepository;
    @MockitoBean SellerApplicationRepository sellerApplicationRepository;
    @MockitoBean SellerApplicationDocumentRepository sellerApplicationDocumentRepository;
    @MockitoBean SellerRepository sellerRepository;
    @MockitoBean AdminAccountRepository adminAccountRepository;
    @MockitoBean MemberRestrictionRepository memberRestrictionRepository;
    @MockitoBean AdminActionLogRepository adminActionLogRepository;
    @MockitoBean MemberRoleAssignmentRepository memberRoleAssignmentRepository;
    @MockitoBean InquiryRepository inquiryRepository;
    @MockitoBean InquiryAnswerRepository inquiryAnswerRepository;
    @MockitoBean WithdrawalRequestRepository withdrawalRequestRepository;

    @Test
    void contextLoads() {
    }
}
