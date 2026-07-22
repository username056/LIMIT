package com.c203.limit.domain.member.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "user_account")
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    @Column(name = "member_type", nullable = false, length = 20)
    private MemberRole role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemberStatus status;
    @Column(name = "marketing_opt_in", nullable = false)
    private boolean isMarketingOptIn;
    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;
    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;
    @Column(name = "password_changed_at")
    private OffsetDateTime passwordChangedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Member() {}

    public static Member createLocal(String email, String encodedPassword, String nickname, String phone) {
        Member member = new Member();
        member.email = email;
        member.password = encodedPassword;
        member.nickname = nickname;
        member.phone = phone;
        member.role = MemberRole.BUYER;
        member.status = MemberStatus.ACTIVE;
        member.createdAt = OffsetDateTime.now();
        member.updatedAt = member.createdAt;
        return member;
    }
    public static Member createSocial(String email, String nickname) {
        Member member = createLocal(email, null, nickname, null);
        return member;
    }

    public void updateProfile(String nickname, String phone) {
        if (nickname != null) this.nickname = nickname;
        if (phone != null) this.phone = phone;
        this.updatedAt = OffsetDateTime.now();
    }
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordChangedAt = OffsetDateTime.now();
        this.updatedAt = this.passwordChangedAt;
    }
    public void recordLogin() { this.lastLoginAt = OffsetDateTime.now(); }
    public void grantSellerRole() { this.role = MemberRole.SELLER; }
    public void markWithdrawalPending() { this.status = MemberStatus.WITHDRAWAL_PENDING; this.updatedAt = OffsetDateTime.now(); }
    public void completeWithdrawal() { this.status = MemberStatus.WITHDRAWN; this.updatedAt = OffsetDateTime.now(); }
    public void reactivate() { this.status = MemberStatus.ACTIVE; this.updatedAt = OffsetDateTime.now(); }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getNickname() { return nickname; }
    public String getPhone() { return phone; }
    public MemberRole getRole() { return role; }
    public MemberStatus getStatus() { return status; }
    public boolean isMarketingOptIn() { return isMarketingOptIn; }
    public OffsetDateTime getEmailVerifiedAt() { return emailVerifiedAt; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
