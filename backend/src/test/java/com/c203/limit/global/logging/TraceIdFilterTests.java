package com.c203.limit.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 요청에 X-Trace-Id가 있으면 그대로 재사용하고 없으면 새로 발급하는지, 응답 헤더와 MDC에 반영되는지,
 * 그리고 필터 체인에서 예외가 나도 MDC가 반드시 정리되는지(finally) 확인한다.
 */
class TraceIdFilterTests {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void reusesTheIncomingTraceIdHeaderWhenPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "incoming-trace-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("incoming-trace-id");
    }

    @Test
    void generatesANewTraceIdWhenHeaderIsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isNotBlank();
    }

    @Test
    void generatesANewTraceIdWhenHeaderIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    void putsTraceIdIntoMdcWhileTheChainRunsThenRemovesItAfterward() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "trace-during-chain");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] mdcDuringChain = new String[1];
        FilterChain chain = (req, res) -> mdcDuringChain[0] = MDC.get("traceId");

        filter.doFilter(request, response, chain);

        assertThat(mdcDuringChain[0]).isEqualTo("trace-during-chain");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void clearsMdcEvenWhenTheFilterChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new RuntimeException("downstream failure")).when(chain)
                .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("downstream failure");
        assertThat(MDC.get("traceId")).isNull();
    }
}
