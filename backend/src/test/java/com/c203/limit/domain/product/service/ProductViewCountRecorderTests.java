package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    /** 숨김·판매 완료·삭제 매물은 공개 조회수 집계 대상이 아니다. */
    @Test
    void doesNotRecordWhenListingIsNotPublic() {
        when(listingRepository.increaseViewCount(7L)).thenReturn(0);

        assertThat(recorder.record(7L)).isFalse();
    }

    /** 트랜잭션 프록시 밖의 디스패처가 종료 단계 예외까지 처리할 수 있도록 실패를 전파한다. */
    @Test
    void propagatesFailureToDispatcher() {
        when(listingRepository.increaseViewCount(7L))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> recorder.record(7L))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void ignoresNullProductId() {
        assertThat(recorder.record(null)).isFalse();
        verify(listingRepository, never()).increaseViewCount(null);
    }
}
