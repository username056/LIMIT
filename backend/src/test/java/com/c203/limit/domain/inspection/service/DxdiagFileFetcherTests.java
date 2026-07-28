package com.c203.limit.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DxdiagFileFetcherTests {

    private static final String FILE_URL = "https://cdn.example.com/evidence/9004.xml";

    @Test
    void fetchReturnsFileBytesOnSuccess() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var fetcher = new DxdiagFileFetcher(builder);

        server.expect(requestTo(FILE_URL))
                .andRespond(withSuccess("<DxDiag></DxDiag>", MediaType.APPLICATION_XML));

        byte[] bytes = fetcher.fetch(FILE_URL);

        assertThat(new String(bytes)).isEqualTo("<DxDiag></DxDiag>");
        server.verify();
    }

    @Test
    void fetchThrowsBusinessExceptionWhenDownloadFails() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var fetcher = new DxdiagFileFetcher(builder);

        server.expect(requestTo(FILE_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> fetcher.fetch(FILE_URL))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.DXDIAG_FILE_FETCH_FAILED));
        server.verify();
    }
}
