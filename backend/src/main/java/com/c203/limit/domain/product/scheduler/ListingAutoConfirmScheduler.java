package com.c203.limit.domain.product.scheduler;

import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.service.ListingService;
import com.c203.limit.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * autoConfirmAt이 지난 INSPECTING 매물을 주기적으로 찾아 자동 구매확정 처리한다. 구매자가 검수
 * 기한 안에 직접 구매확정을 누르지 않아도 에스크로가 무기한 묶이지 않게 하는 안전망이다.
 *
 * <p>{@code limit.product.auto-confirm.enabled=false}로 끌 수 있다 — 테스트에서 {@code
 * @Scheduled} 자동 실행과 직접 호출이 경합하지 않도록 끄거나, Blue/Green 배포에서 특정 인스턴스만
 * 실행하도록 제한할 때 사용한다.
 */
@Component
@ConditionalOnProperty(
        prefix = "limit.product.auto-confirm",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ListingAutoConfirmScheduler {
    private static final Logger log = LoggerFactory.getLogger(ListingAutoConfirmScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final ListingRepository listingRepository;
    private final ListingService listingService;
    private final Clock clock;

    public ListingAutoConfirmScheduler(
            ListingRepository listingRepository, ListingService listingService, Clock clock) {
        this.listingRepository = listingRepository;
        this.listingService = listingService;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${limit.product.auto-confirm.initial-delay-ms:60000}",
            fixedDelayString = "${limit.product.auto-confirm.fixed-delay-ms:3600000}")
    public void autoConfirmOverdueInspections() {
        List<Long> targets = listingRepository.findAutoConfirmCandidateIds(
                LocalDateTime.now(clock), PageRequest.of(0, BATCH_SIZE));

        if (targets.isEmpty()) {
            return;
        }

        int confirmed = 0;
        int failed = 0;
        for (Long listingId : targets) {
            try {
                listingService.autoConfirm(listingId);
                confirmed++;
            } catch (BusinessException exception) {
                failed++;
                log.warn(
                        "auto confirm rejected for listingId={}: errorCode={}",
                        listingId,
                        exception.getErrorCode().getCode());
            } catch (RuntimeException exception) {
                failed++;
                log.warn(
                        "auto confirm failed for listingId={}: {}",
                        listingId,
                        exception.getClass().getSimpleName());
            }
        }

        log.info(
                "auto confirm batch finished: targets={}, confirmed={}, failed={}",
                targets.size(),
                confirmed,
                failed);
    }
}
