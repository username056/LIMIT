package com.c203.limit.domain.place.service;

import com.c203.limit.domain.place.dto.response.PlaceSearchResultResponse;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class PlaceSearchService {
    private static final String KEYWORD_SEARCH_URI =
            "https://dapi.kakao.com/v2/local/search/keyword.json?query={query}&size={size}";

    private final RestClient restClient = RestClient.create();
    private final String kakaoRestApiKey;

    public PlaceSearchService(
            @Value("${limit.place.kakao-rest-api-key:}") String kakaoRestApiKey) {
        this.kakaoRestApiKey = kakaoRestApiKey;
    }

    public List<PlaceSearchResultResponse> search(String query, int size) {
        if (!StringUtils.hasText(kakaoRestApiKey)) {
            throw new BusinessException(ErrorCode.PLACE_SEARCH_UNAVAILABLE);
        }
        try {
            Map<String, Object> body =
                    restClient
                            .get()
                            .uri(KEYWORD_SEARCH_URI, query, size)
                            .headers(headers -> headers.set("Authorization", "KakaoAK " + kakaoRestApiKey))
                            .retrieve()
                            .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
            return toResults(body);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.PLACE_SEARCH_UNAVAILABLE);
        }
    }

    @SuppressWarnings("unchecked")
    private List<PlaceSearchResultResponse> toResults(Map<String, Object> body) {
        Object documents = body == null ? null : body.get("documents");
        if (!(documents instanceof List<?> list) || CollectionUtils.isEmpty(list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .map(
                        item ->
                                new PlaceSearchResultResponse(
                                        text(item, "place_name"),
                                        text(item, "address_name"),
                                        text(item, "road_address_name"),
                                        text(item, "category_group_name"),
                                        parseCoordinate(item, "y"),
                                        parseCoordinate(item, "x")))
                .toList();
    }

    private String text(Map<String, Object> item, String key) {
        Object value = item.get(key);
        return value == null ? "" : value.toString();
    }

    private double parseCoordinate(Map<String, Object> item, String key) {
        Object value = item.get(key);
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }
}
