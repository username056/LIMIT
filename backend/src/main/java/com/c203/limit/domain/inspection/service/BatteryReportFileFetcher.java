package com.c203.limit.domain.inspection.service;

import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 증거의 cdnUrl에서 배터리 리포트 HTML 파일 바이트를 내려받는다. */
@Component
public class BatteryReportFileFetcher {

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
            throw new BusinessException(ErrorCode.BATTERY_REPORT_FILE_FETCH_FAILED);
        }
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.BATTERY_REPORT_FILE_FETCH_FAILED);
        }
        return bytes;
    }
}
