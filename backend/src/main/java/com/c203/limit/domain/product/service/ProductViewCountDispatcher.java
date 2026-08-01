package com.c203.limit.domain.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** 상세 응답 스레드와 조회수 DB 트랜잭션을 분리한다. */
@Service
public class ProductViewCountDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ProductViewCountDispatcher.class);

    private final ProductViewCountRecorder recorder;

    public ProductViewCountDispatcher(ProductViewCountRecorder recorder) {
        this.recorder = recorder;
    }

    @Async("productViewCountExecutor")
    public void dispatch(Long productId) {
        try {
            recorder.record(productId);
        } catch (RuntimeException exception) {
            // recorder 프록시의 트랜잭션 종료·커밋 실패까지 이 경계에서 처리한다.
            log.warn(
                    "product view count not recorded: productId={}, cause={}",
                    productId,
                    exception.getClass().getSimpleName());
        }
    }
}
