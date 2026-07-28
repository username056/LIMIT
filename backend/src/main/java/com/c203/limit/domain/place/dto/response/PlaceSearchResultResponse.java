package com.c203.limit.domain.place.dto.response;

public record PlaceSearchResultResponse(
        String placeName,
        String addressName,
        String roadAddressName,
        String categoryGroupName,
        double latitude,
        double longitude) {}
