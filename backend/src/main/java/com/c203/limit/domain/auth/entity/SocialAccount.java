package com.c203.limit.domain.auth.entity;

import java.time.OffsetDateTime;
import com.c203.limit.domain.member.entity.Member;
import jakarta.persistence.*;

@Entity
@Table(name = "social_account", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"}))
public class SocialAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_account_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id") private Member member;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SocialProvider provider;
    @Column(name = "provider_user_id", nullable = false, length = 255) private String providerUserId;
    @Column(name = "provider_email", length = 255) private String providerEmail;
    @Column(name = "linked_at", nullable = false) private OffsetDateTime linkedAt;
    protected SocialAccount() {}
    public static SocialAccount link(Member member, SocialProvider provider, String providerUserId, String providerEmail) {
        SocialAccount account = new SocialAccount(); account.member = member; account.provider = provider;
        account.providerUserId = providerUserId; account.providerEmail = providerEmail; account.linkedAt = OffsetDateTime.now();
        return account;
    }
    public Long getId() { return id; } public Member getMember() { return member; }
    public SocialProvider getProvider() { return provider; } public String getProviderEmail() { return providerEmail; }
    public OffsetDateTime getLinkedAt() { return linkedAt; }
}
