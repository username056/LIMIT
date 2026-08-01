package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.repository.ListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class ProductViewCountRecorderTests {

    @Mock ListingRepository listingRepository;
    ProductViewCountRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new ProductViewCountRecorder(listingRepository);
    }

    @Test
    void recordsViewForPublicListing() {
        when(listingRepository.increaseViewCount(7L)).thenReturn(1);

        assertThat(recorder.record(7L)).isTrue();
    }

    /** 숨김·판매 완료·삭제 매물의 조회수가 올라가면 인기순이 그 매물을 끌어올린다. */
    @Test
    void doesNotRecordWhenListingIsNotPublic() {
        when(listingRepository.increaseViewCount(7L)).thenReturn(0);

        assertThat(recorder.record(7L)).isFalse();
    }

    /** 집계는 부가 기능이다. 실패가 상세 조회를 막으면 손해가 훨씬 크다. */
    @Test
    void swallowsFailureSoDetailStillResponds() {
        when(listingRepository.increaseViewCount(7L))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThat(recorder.record(7L)).isFalse();
    }

    @Test
    void ignoresNullProductId() {
        assertThat(recorder.record(null)).isFalse();
        verify(listingRepository, never()).increaseViewCount(null);
    }
}
