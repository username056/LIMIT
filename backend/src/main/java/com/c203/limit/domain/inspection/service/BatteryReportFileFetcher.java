package com.c203.limit.domain.inspection.service;

import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 증거의 cdnUrl에서 배터리 리포트 HTML 파일 바이트를 내려받는다. */
@Component
public class BatteryReportFileFetcher {

    private static final Logger log = LoggerFactory.getLogger(BatteryReportFileFetcher.class);

    private final RestClient restClient;

    public BatteryReportFileFetcher(
            @Qualifier("batteryReportFileRestClientBuilder") RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public byte[] fetch(String fileUrl) {
        byte[] bytes;
        try {
            bytes = restClient.get().uri(fileUrl).retrieve().body(byte[].class);
        } catch (RestClientException exception) {
            log.warn("battery report file fetch failed: fileUrl={}", fileUrl, exception);
            throw new BusinessException(ErrorCode.BATTERY_REPORT_FILE_FETCH_FAILED);
        }
        if (bytes == null || bytes.length == 0) {
            log.warn("battery report file fetch returned empty body: fileUrl={}", fileUrl);
            throw new BusinessException(ErrorCode.BATTERY_REPORT_FILE_FETCH_FAILED);
        }
        return bytes;
    }
}
