package com.c203.limit.domain.inspection.dto;

import java.math.BigDecimal;

/**
 * OCR 엔진이 인식한 텍스트 조각 하나. left/top/right/bottom은 원본 이미지 픽셀 좌표계의 바운딩 박스로,
 * 라벨과 값이 텍스트 스트림 상에서 떨어져 있는 카드형·표 레이아웃에서 같은 행/열에 속하는 조각을 찾는 데 쓰인다.
 */
public record OcrToken(String text, BigDecimal confidence, double left, double top, double right, double bottom) {

    public double centerX() {
        return (left + right) / 2.0;
    }

    public double centerY() {
        return (top + bottom) / 2.0;
    }

    public double height() {
        return bottom - top;
    }
}
