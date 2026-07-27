package com.c203.limit.domain.product.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.product.dto.response.FavoriteProductResponse;
import com.c203.limit.domain.product.dto.response.FavoriteStatusResponse;
import com.c203.limit.domain.product.service.WishlistCreateResult;
import com.c203.limit.domain.product.service.WishlistService;
import com.c203.limit.domain.product.service.WishlistService.FavoritePage;
import com.c203.limit.global.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class WishlistControllerTests {
    private static final Long MEMBER_ID = 20L;
    private static final Long PRODUCT_ID = 100L;

    @Mock WishlistService wishlistService;
    @Mock CurrentUser currentUser;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WishlistController(wishlistService, currentUser)).build();
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
    }

    @Test
    void returns201WhenFavoriteIsCreated() throws Exception {
        when(wishlistService.add(MEMBER_ID, PRODUCT_ID))
                .thenReturn(WishlistCreateResult.created(response()));

        mockMvc.perform(post("/api/v1/products/{productId}/favorites", PRODUCT_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.favoriteId").value(501L))
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_ID));
    }

    @Test
    void returns200WhenFavoriteAlreadyExists() throws Exception {
        when(wishlistService.add(MEMBER_ID, PRODUCT_ID))
                .thenReturn(WishlistCreateResult.existing(response()));

        mockMvc.perform(post("/api/v1/products/{productId}/favorites", PRODUCT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void returnsCurrentMembersFavorites() throws Exception {
        when(wishlistService.findAll(MEMBER_ID, 0, 20))
                .thenReturn(new FavoritePage(List.of(response()), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/v1/members/me/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Galaxy S24"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void returnsCurrentMembersFavoriteStatus() throws Exception {
        when(wishlistService.status(MEMBER_ID, PRODUCT_ID))
                .thenReturn(new FavoriteStatusResponse(true));

        mockMvc.perform(get("/api/v1/products/{productId}/favorites/me", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorite").value(true));
    }

    @Test
    void removesFavoriteIdempotently() throws Exception {
        mockMvc.perform(delete("/api/v1/products/{productId}/favorites", PRODUCT_ID))
                .andExpect(status().isNoContent());

        verify(wishlistService).remove(MEMBER_ID, PRODUCT_ID);
    }

    private FavoriteProductResponse response() {
        return new FavoriteProductResponse(
                501L,
                PRODUCT_ID,
                "Galaxy S24",
                "Samsung",
                "Galaxy S24",
                BigDecimal.valueOf(650000),
                "ON_SALE",
                LocalDateTime.of(2026, 7, 27, 10, 0));
    }
}
