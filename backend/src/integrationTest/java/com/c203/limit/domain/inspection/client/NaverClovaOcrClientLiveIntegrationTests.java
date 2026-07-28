package com.c203.limit.domain.inspection.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.config.NaverClovaOcrProperties;
import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 실제 네이버 클로바 OCR 서버를 호출하는 라이브 테스트. NAVER_CLOVA_OCR_INVOKE_URL /
 * NAVER_CLOVA_OCR_SECRET_KEY 환경변수가 없으면 스킵된다. full-infrastructure 태그로 기본
 * test/integrationTest에서는 제외되고 `gradle infrastructureTest`로만 실행된다.
 */
@Tag("full-infrastructure")
class NaverClovaOcrClientLiveIntegrationTests {

    private HttpServer imageServer;

    @AfterEach
    void tearDown() {
        if (imageServer != null) {
            imageServer.stop(0);
        }
    }

    @Test
    void recognizesTextFromRealNaverClovaOcrServer() throws IOException {
        String invokeUrl = System.getenv("NAVER_CLOVA_OCR_INVOKE_URL");
        String secretKey = System.getenv("NAVER_CLOVA_OCR_SECRET_KEY");
        Assumptions.assumeTrue(
                invokeUrl != null && !invokeUrl.isBlank() && secretKey != null && !secretKey.isBlank(),
                "NAVER_CLOVA_OCR_INVOKE_URL / NAVER_CLOVA_OCR_SECRET_KEY not configured");

        byte[] imageBytes = renderTestImage("LIMIT TEST 1234");
        String imageUrl = startImageServer(imageBytes);

        NaverClovaOcrProperties properties = new NaverClovaOcrProperties();
        properties.setInvokeUrl(invokeUrl);
        properties.setSecretKey(secretKey);
        NaverClovaOcrClient client = new NaverClovaOcrClient(RestClient.builder(), properties);

        NaverClovaOcrResult result = client.recognize(imageUrl, "jpg");

        assertThat(result.rawText()).isNotBlank();
        assertThat(result.confidence()).isNotNull();
        assertThat(result.modelVersion()).isNotBlank();
    }

    private byte[] renderTestImage(String text) throws IOException {
        BufferedImage image = new BufferedImage(400, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 32));
        graphics.drawString(text, 20, 70);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }

    private String startImageServer(byte[] imageBytes) throws IOException {
        imageServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        imageServer.createContext(
                "/test-image.jpg",
                exchange -> {
                    exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
                    exchange.sendResponseHeaders(200, imageBytes.length);
                    exchange.getResponseBody().write(imageBytes);
                    exchange.close();
                });
        imageServer.start();
        return "http://localhost:" + imageServer.getAddress().getPort() + "/test-image.jpg";
    }
}
