package com.c203.limit.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserTests {
    private CurrentUser currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new CurrentUser();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireRejectsMissingAuthentication() {
        assertThatThrownBy(() -> currentUser.require())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void requireRejectsAnonymousAuthentication() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new AnonymousAuthenticationToken(
                                "anonymous-key",
                                "anonymousUser",
                                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThatThrownBy(() -> currentUser.require())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void requireRejectsPrincipalOfUnexpectedType() {
        authenticate("plain-string-principal");

        assertThatThrownBy(() -> currentUser.require())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void requireReturnsAuthenticatedPrincipal() {
        AuthenticatedUser principal = new AuthenticatedUser(9L, "MEMBER", Set.of("MEMBER"));
        authenticate(principal);

        assertThat(currentUser.require()).isSameAs(principal);
    }

    @Test
    void memberIdReturnsIdForMemberAccount() {
        authenticate(new AuthenticatedUser(9L, "MEMBER", Set.of("MEMBER")));

        assertThat(currentUser.memberId()).isEqualTo(9L);
    }

    @Test
    void memberIdRejectsAdminAccountWithForbidden() {
        authenticate(new AuthenticatedUser(1L, "ADMIN", Set.of("SUPER_ADMIN")));

        assertThatThrownBy(() -> currentUser.memberId())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void memberIdRejectsMissingAuthenticationWithUnauthorized() {
        assertThatThrownBy(() -> currentUser.memberId())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void memberIdOrNullReturnsNullWithoutAuthentication() {
        assertThat(currentUser.memberIdOrNull()).isNull();
    }

    @Test
    void memberIdOrNullReturnsNullForPrincipalOfUnexpectedType() {
        authenticate("plain-string-principal");

        assertThat(currentUser.memberIdOrNull()).isNull();
    }

    @Test
    void memberIdOrNullReturnsNullForAdminAccount() {
        authenticate(new AuthenticatedUser(1L, "ADMIN", Set.of("SUPER_ADMIN")));

        assertThat(currentUser.memberIdOrNull()).isNull();
    }

    @Test
    void memberIdOrNullReturnsIdForMemberAccount() {
        authenticate(new AuthenticatedUser(42L, "MEMBER", Set.of("MEMBER")));

        assertThat(currentUser.memberIdOrNull()).isEqualTo(42L);
    }

    @Test
    void adminIdReturnsIdForAdminAccount() {
        authenticate(new AuthenticatedUser(3L, "ADMIN", Set.of("SUPER_ADMIN")));

        assertThat(currentUser.adminId()).isEqualTo(3L);
    }

    @Test
    void adminIdRejectsMemberAccountWithForbidden() {
        authenticate(new AuthenticatedUser(9L, "MEMBER", Set.of("MEMBER")));

        assertThatThrownBy(() -> currentUser.adminId())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void adminIdRejectsMissingAuthenticationWithUnauthorized() {
        assertThatThrownBy(() -> currentUser.adminId())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void doesNotLeakAuthenticationBetweenLookupsAfterContextIsCleared() {
        authenticate(new AuthenticatedUser(9L, "MEMBER", Set.of("MEMBER")));
        assertThat(currentUser.memberIdOrNull()).isEqualTo(9L);

        SecurityContextHolder.clearContext();

        assertThat(currentUser.memberIdOrNull()).isNull();
    }

    private void authenticate(Object principal) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
    }
}
