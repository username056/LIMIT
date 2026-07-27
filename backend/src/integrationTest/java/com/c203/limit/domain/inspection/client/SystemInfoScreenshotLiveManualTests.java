package com.c203.limit.domain.inspection.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.config.NaverClovaOcrProperties;
import com.c203.limit.domain.inspection.dto.OcrFieldExtraction;
import com.c203.limit.domain.inspection.dto.OcrToken;
import com.c203.limit.domain.inspection.parser.SystemInfoScreenshotParser;
import com.c203.limit.domain.inspection.service.OcrFieldExpectations;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 실제 "설정 > 시스템 > 정보" 캡처 이미지로 (1) 네이버 클로바가 반환하는 원문 토큰과 (2)
 * SystemInfoScreenshotParser가 뽑아내는 구조화 결과를 눈으로 확인하기 위한 1회성 수동 진단 테스트.
 *
 * <p>로컬 이미지 파일 경로가 없거나 NAVER_CLOVA_OCR_* 환경변수가 없으면 스킵된다. 라벨/패턴 검증이 끝나면
 * 삭제해도 되는 테스트다.
 */
@Tag("full-infrastructure")
class SystemInfoScreenshotLiveManualTests {

    private static final Path IMAGE_PATH =
            Path.of("C:\\Users\\SSAFY\\Pictures\\Screenshots\\comm_test.png");

    private HttpServer imageServer;

    @AfterEach
    void tearDown() {
        if (imageServer != null) {
            imageServer.stop(0);
        }
    }

    @Test
    void printsRawTokensAndParsedFieldsFromRealScreenshot() throws IOException {
        String invokeUrl = System.getenv("NAVER_CLOVA_OCR_INVOKE_URL");
        String secretKey = System.getenv("NAVER_CLOVA_OCR_SECRET_KEY");
        Assumptions.assumeTrue(
                invokeUrl != null && !invokeUrl.isBlank() && secretKey != null && !secretKey.isBlank(),
                "NAVER_CLOVA_OCR_INVOKE_URL / NAVER_CLOVA_OCR_SECRET_KEY not configured");
        Assumptions.assumeTrue(Files.exists(IMAGE_PATH), "이미지 파일이 없습니다: " + IMAGE_PATH);

        byte[] imageBytes = Files.readAllBytes(IMAGE_PATH);
        String imageUrl = startImageServer(imageBytes);

        NaverClovaOcrProperties properties = new NaverClovaOcrProperties();
        properties.setInvokeUrl(invokeUrl);
        properties.setSecretKey(secretKey);
        NaverClovaOcrClient client = new NaverClovaOcrClient(RestClient.builder(), properties);

        List<OcrToken> tokens = client.recognizeFields(imageUrl, "png");

        // stdout이 UTF-8이 아닌 콘솔 코드페이지로 한글을 깨뜨리는 걸 막기 위해 UTF-8로 명시해서 출력한다.
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

        out.println("===== RAW TOKENS (" + tokens.size() + " fields) =====");
        for (int i = 0; i < tokens.size(); i++) {
            OcrToken token = tokens.get(i);
            out.println(
                    "[" + i + "] box=(" + token.left() + "," + token.top() + ")-(" + token.right() + ","
                            + token.bottom() + ") confidence=" + token.confidence() + " text=\"" + token.text()
                            + "\"");
        }

        List<OcrFieldExtraction> extractions =
                new SystemInfoScreenshotParser().parse(tokens, OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        out.println(
                "===== PARSED FIELDS (" + extractions.size() + "/"
                        + OcrFieldExpectations.SCREENSHOT_FIELD_TYPES.size() + " expected) =====");
        for (OcrFieldExtraction extraction : extractions) {
            out.println(
                    extraction.fieldType()
                            + " -> rawText=\"" + extraction.rawText()
                            + "\", parsedValue=\"" + extraction.parsedValue()
                            + "\", confidence=" + extraction.confidence());
        }

        assertThat(tokens).isNotEmpty();
    }

    private String startImageServer(byte[] imageBytes) throws IOException {
        imageServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        imageServer.createContext(
                "/comm_test.png",
                exchange -> {
                    exchange.getResponseHeaders().add("Content-Type", "image/png");
                    exchange.sendResponseHeaders(200, imageBytes.length);
                    exchange.getResponseBody().write(imageBytes);
                    exchange.close();
                });
        imageServer.start();
        return "http://localhost:" + imageServer.getAddress().getPort() + "/comm_test.png";
    }
}
