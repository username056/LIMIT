package com.c203.limit.domain.inspection.client;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * OCR 인식률이 낮을 때 재시도용으로 이미지를 그레이스케일 변환 후 명암 대비를 최대로 늘려(히스토그램 스트레칭)
 * 텍스트와 배경의 경계를 뚜렷하게 만든다. 외부 이미지 처리 라이브러리 없이 JDK 표준 API(ImageIO/BufferedImage)
 * 만으로 구현한다.
 */
@Component
public class OcrImagePreprocessor {

    private static final String OUTPUT_FORMAT = "png";

    public byte[] enhanceContrast(byte[] originalImageBytes) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalImageBytes));
            if (original == null) {
                throw new IllegalArgumentException("Cannot decode image bytes for OCR preprocessing");
            }
            BufferedImage grayscale = toGrayscale(original);
            BufferedImage contrastEnhanced = stretchContrast(grayscale);
            return encode(contrastEnhanced);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to preprocess OCR image", exception);
        }
    }

    private BufferedImage toGrayscale(BufferedImage source) {
        BufferedImage grayscale =
                new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = grayscale.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return grayscale;
    }

    /** 실제로 쓰이는 밝기 범위[min,max]를 [0,255] 전체로 선형 확장한다. */
    private BufferedImage stretchContrast(BufferedImage grayscale) {
        int width = grayscale.getWidth();
        int height = grayscale.getHeight();
        int min = 255;
        int max = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = grayscale.getRaster().getSample(x, y, 0);
                min = Math.min(min, gray);
                max = Math.max(max, gray);
            }
        }
        if (max <= min) {
            return grayscale;
        }
        float scale = 255f / (max - min);
        float offset = -min * scale;
        RescaleOp rescaleOp = new RescaleOp(scale, offset, null);
        return rescaleOp.filter(grayscale, null);
    }

    private byte[] encode(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, OUTPUT_FORMAT, output);
        return output.toByteArray();
    }
}
