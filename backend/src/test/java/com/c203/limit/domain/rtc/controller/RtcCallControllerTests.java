package com.c203.limit.domain.rtc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.rtc.dto.request.CreateCallRequest;
import com.c203.limit.domain.rtc.dto.request.RespondCallRequest;
import com.c203.limit.domain.rtc.dto.response.CallResponse;
import com.c203.limit.domain.rtc.service.RtcCallService;
import com.c203.limit.global.security.CurrentUser;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RtcCallControllerTests {
    @Mock RtcCallService service;
    @Mock CurrentUser currentUser;
    RtcCallController controller;

    @BeforeEach
    void setUp() {
        controller = new RtcCallController(service, currentUser);
        when(currentUser.memberId()).thenReturn(20L);
    }

    @Test
    void requestsCallForAuthenticatedMember() {
        CreateCallRequest request = new CreateCallRequest(LocalDateTime.now().plusMinutes(5), "외관 확인");
        CallResponse response = new CallResponse(
                1L, 10L, 20L, 30L, "PROPOSED", request.scheduledAt(), request.memo(), null, null, false);
        when(service.request(10L, 20L, request)).thenReturn(response);

        var result = controller.request(10L, request);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void respondsToCallAsCurrentMember() {
        RespondCallRequest request = new RespondCallRequest(true, null);

        controller.respond(1L, request);

        verify(service).respond(1L, 20L, request);
    }
}
