package com.c203.limit.global.bootstrap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.bootstrap.AdminBootstrapService;
import com.c203.limit.domain.auth.bootstrap.MemberBootstrapService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

@ExtendWith(MockitoExtension.class)
class BaseInitDataTests {
    @Mock MemberBootstrapService memberBootstrapService;
    @Mock AdminBootstrapService adminBootstrapService;

    @Test
    void createsConfiguredMemberAndOperatorWithoutExposingCredentials() throws Exception {
        BaseInitDataProperties properties = properties();
        when(memberBootstrapService.ensureVerifiedMember(
                        "member@limit.local", "MemberTest123!", "테스트회원", "01000000000"))
                .thenReturn(true);
        when(adminBootstrapService.ensureAccount(
                        "operator@limit.local", "OperatorTest123!", "테스트운영자", "OPERATOR"))
                .thenReturn(true);

        new BaseInitData(memberBootstrapService, adminBootstrapService, properties)
                .run(mock(ApplicationArguments.class));

        verify(memberBootstrapService)
                .ensureVerifiedMember(
                        "member@limit.local", "MemberTest123!", "테스트회원", "01000000000");
        verify(adminBootstrapService)
                .ensureAccount(
                        "operator@limit.local", "OperatorTest123!", "테스트운영자", "OPERATOR");
    }

    @Test
    void skipsIndividuallyDisabledAccounts() throws Exception {
        BaseInitDataProperties properties = properties();
        properties.getMember().setEnabled(false);
        properties.getAdmin().setEnabled(false);

        new BaseInitData(memberBootstrapService, adminBootstrapService, properties)
                .run(mock(ApplicationArguments.class));

        verify(memberBootstrapService, never())
                .ensureVerifiedMember(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        verify(adminBootstrapService, never())
                .ensureAccount(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    private BaseInitDataProperties properties() {
        BaseInitDataProperties properties = new BaseInitDataProperties();
        properties.getMember().setEmail("member@limit.local");
        properties.getMember().setPassword("MemberTest123!");
        properties.getMember().setNickname("테스트회원");
        properties.getMember().setPhone("01000000000");
        properties.getAdmin().setEmail("operator@limit.local");
        properties.getAdmin().setPassword("OperatorTest123!");
        properties.getAdmin().setName("테스트운영자");
        return properties;
    }
}
