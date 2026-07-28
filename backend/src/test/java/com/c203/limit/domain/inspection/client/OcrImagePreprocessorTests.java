package com.c203.limit.domain.inspection.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class OcrImagePreprocessorTests {

    private final OcrImagePreprocessor preprocessor = new OcrImagePreprocessor();

    @Test
    void stretchesLowContrastImageToFullBrightnessRange() throws IOException {
        // 밝기가 100~150 사이로만 몰려 있는 저대비 이미지를 만든다.
        byte[] lowContrastImage = lowContrastGrayscaleImage(40, 40, 100, 150);

        byte[] result = preprocessor.enhanceContrast(lowContrastImage);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(decoded).isNotNull();
        int[] range = grayscaleRange(decoded);
        assertThat(range[0]).isEqualTo(0);
        assertThat(range[1]).isEqualTo(255);
    }

    @Test
    void throwsForUndecodableBytes() {
        byte[] garbage = {1, 2, 3, 4, 5};

        assertThatThrownBy(() -> preprocessor.enhanceContrast(garbage))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 왼쪽 절반은 밝기 low, 오른쪽 절반은 밝기 high인 그레이스케일 이미지를 PNG 바이트로 만든다. */
    private byte[] lowContrastGrayscaleImage(int width, int height, int low, int high) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(low, low, low));
        graphics.fillRect(0, 0, width / 2, height);
        graphics.setColor(new Color(high, high, high));
        graphics.fillRect(width / 2, 0, width / 2, height);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private int[] grayscaleRange(BufferedImage image) {
        int min = 255;
        int max = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int gray = image.getRaster().getSample(x, y, 0);
                min = Math.min(min, gray);
                max = Math.max(max, gray);
            }
        }
        return new int[] {min, max};
    }
}
