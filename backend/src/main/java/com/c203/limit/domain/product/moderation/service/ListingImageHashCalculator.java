package com.c203.limit.domain.product.moderation.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class ListingImageHashCalculator {
    public ImageHashes calculate(byte[] content) {
        String sha256 = sha256(content);
        String perceptualHash = differenceHash(content);
        return new ImageHashes(sha256, perceptualHash);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String differenceHash(byte[] content) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) return null;
            BufferedImage resized = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D graphics = resized.createGraphics();
            try {
                graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(source, 0, 0, 9, 8, null);
            } finally {
                graphics.dispose();
            }
            long hash = 0L;
            int bit = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int left = resized.getRaster().getSample(x, y, 0);
                    int right = resized.getRaster().getSample(x + 1, y, 0);
                    if (left > right) hash |= 1L << bit;
                    bit++;
                }
            }
            return String.format("%016x", hash);
        } catch (Exception exception) {
            return null;
        }
    }

    public record ImageHashes(String sha256, String perceptualHash) {}
}
