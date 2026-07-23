package com.c203.limit.domain.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원의 매물 찜. DDL: wishlist. user_id는 회원 도메인 참조라 FK 없이 ID만 보관한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "wishlist")
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wishlist_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id")
    private Listing listing;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Wishlist create(Long userId, Listing listing) {
        Wishlist wishlist = new Wishlist();
        wishlist.userId = userId;
        wishlist.listing = listing;
        wishlist.createdAt = LocalDateTime.now();
        return wishlist;
    }
}
