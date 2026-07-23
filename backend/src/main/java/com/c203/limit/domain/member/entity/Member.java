package com.c203.limit.domain.member.entity;

import com.c203.limit.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_account")
public class Member extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemberStatus status;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean isMarketingOptIn;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    protected Member() {}

    public static Member createLocal(
            String email, String encodedPassword, String nickname, String phone) {
        return createLocal(email, encodedPassword, nickname, phone, false);
    }

    public static Member createLocal(
            String email,
            String encodedPassword,
            String nickname,
            String phone,
            boolean isMarketingOptIn) {
        Member member = new Member();
        member.email = email;
        member.password = encodedPassword;
        member.nickname = nickname;
        member.phone = phone;
        member.isMarketingOptIn = isMarketingOptIn;
        member.status = MemberStatus.ACTIVE;
        return member;
    }

    public static Member createSocial(String email, String nickname) {
        return createSocial(email, nickname, null, false);
    }

    public static Member createSocial(
            String email, String nickname, String phone, boolean isMarketingOptIn) {
        Member member = createLocal(email, null, nickname, phone, isMarketingOptIn);
        member.verifyEmail();
        return member;
    }

    public void updateProfile(String nickname, String phone) {
        if (nickname != null) this.nickname = nickname;
        if (phone != null) this.phone = phone;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordChangedAt = LocalDateTime.now();
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void verifyEmail() {
        if (this.emailVerifiedAt == null) {
            this.emailVerifiedAt = LocalDateTime.now();
        }
    }

    public void markWithdrawalPending() {
        this.status = MemberStatus.WITHDRAWAL_PENDING;
    }

    public void completeWithdrawal() {
        this.status = MemberStatus.WITHDRAWN;
    }

    public void reactivate() {
        this.status = MemberStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPhone() {
        return phone;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public boolean isMarketingOptIn() {
        return isMarketingOptIn;
    }

    public LocalDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }
}
