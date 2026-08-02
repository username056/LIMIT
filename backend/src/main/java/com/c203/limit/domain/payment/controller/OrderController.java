package com.c203.limit.domain.payment.controller;

import com.c203.limit.domain.payment.dto.response.OrderSummaryResponse;
import com.c203.limit.domain.payment.service.OrderQueryService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController implements OrderApi {
    private final OrderQueryService orderQueryService;
    private final CurrentUser currentUser;

    public OrderController(OrderQueryService orderQueryService, CurrentUser currentUser) {
        this.orderQueryService = orderQueryService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ApiResponse<List<OrderSummaryResponse>>> listOrders() {
        List<OrderSummaryResponse> response = orderQueryService.listOrders(currentUser.memberId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
