package com.c203.limit.domain.product.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ListingImageHashCalculatorTests {
    private final ListingImageHashCalculator calculator = new ListingImageHashCalculator();

    @Test
    void sameVisualContentProducesStableExactAndPerceptualHashes() throws Exception {
        byte[] image = gradientImage();

        var first = calculator.calculate(image);
        var second = calculator.calculate(image);

        assertThat(first.sha256()).hasSize(64).isEqualTo(second.sha256());
        assertThat(first.perceptualHash()).hasSize(16).isEqualTo(second.perceptualHash());
    }

    @Test
    void nonImageStillGetsExactHashForDuplicateDetection() {
        var hashes = calculator.calculate("not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(hashes.sha256()).hasSize(64);
        assertThat(hashes.perceptualHash()).isNull();
    }

    private byte[] gradientImage() throws Exception {
        BufferedImage image = new BufferedImage(18, 16, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int tone = Math.min(255, x * 12 + y * 2);
                image.setRGB(x, y, new Color(tone, tone, tone).getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
