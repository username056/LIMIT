package com.c203.limit.domain.inspection.repository;

public interface ListingChecklistCountProjection {
    Long getListingId();

    Long getRequiredCount();

    Long getCompletedRequiredCount();

    /**
     * 판매 시작에 꼭 필요한 개인정보 정리 확인 항목 수.
     *
     * <p>촬영 자료는 나중에 채워도 되지만 개인정보 정리는 기기를 넘긴 뒤 되돌릴 수 없어 필수다.
     * 상품 관리 화면이 판매 시작 전에 이 값으로 남은 항목이 있는지 판단한다.
     */
    Long getRequiredConfirmationCount();

    Long getCompletedConfirmationCount();
}
