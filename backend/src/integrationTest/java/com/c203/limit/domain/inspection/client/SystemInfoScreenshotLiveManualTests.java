package com.c203.limit.domain.inspection.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.client.OcrImagePreprocessor;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    private HttpServer imageServer;

    static Stream<Arguments> images() {
        return Stream.of(
                Arguments.of(
                        Path.of("C:\\Users\\SSAFY\\Desktop\\ssafy15th\\comm_proj\\ex\\system_info_cam1.jpg"),
                        "jpg",
                        "image/jpeg"),
                Arguments.of(
                        Path.of(
                                "C:\\Users\\SSAFY\\Desktop\\ssafy15th\\comm_proj\\ex\\system_info_screenshot.png"),
                        "png",
                        "image/png"));
    }

    @AfterEach
    void tearDown() {
        if (imageServer != null) {
            imageServer.stop(0);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("images")
    void printsRawTokensAndParsedFieldsFromRealScreenshot(Path imagePath, String format, String mimeType)
            throws IOException {
        String invokeUrl = System.getenv("NAVER_CLOVA_OCR_INVOKE_URL");
        String secretKey = System.getenv("NAVER_CLOVA_OCR_SECRET_KEY");
        Assumptions.assumeTrue(
                invokeUrl != null && !invokeUrl.isBlank() && secretKey != null && !secretKey.isBlank(),
                "NAVER_CLOVA_OCR_INVOKE_URL / NAVER_CLOVA_OCR_SECRET_KEY not configured");
        Assumptions.assumeTrue(Files.exists(imagePath), "이미지 파일이 없습니다: " + imagePath);

        byte[] originalBytes = Files.readAllBytes(imagePath);
        String fileName = imagePath.getFileName().toString();
        String imageUrl = startImageServer(fileName, originalBytes, mimeType);

        NaverClovaOcrProperties properties = new NaverClovaOcrProperties();
        properties.setInvokeUrl(invokeUrl);
        properties.setSecretKey(secretKey);
        NaverClovaOcrClient client = new NaverClovaOcrClient(RestClient.builder(), properties);

        // stdout이 UTF-8이 아닌 콘솔 코드페이지로 한글을 깨뜨리는 걸 막기 위해 UTF-8로 명시해서 출력한다.
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        out.println("\n########## " + fileName + " (원본, 전처리 없음) ##########");
        List<OcrToken> tokens = client.recognizeFields(imageUrl, format);
        printTokensAndFields(out, tokens);

        out.println("\n########## " + fileName + " (전처리: 그레이스케일+대비 스트레칭) ##########");
        byte[] preprocessedBytes = new OcrImagePreprocessor().enhanceContrast(originalBytes);
        String preprocessedUrl = startImageServer("pre-" + fileName + ".png", preprocessedBytes, "image/png");
        List<OcrToken> preprocessedTokens = client.recognizeFields(preprocessedUrl, "png");
        printTokensAndFields(out, preprocessedTokens);

        assertThat(tokens).isNotEmpty();
    }

    private void printTokensAndFields(PrintStream out, List<OcrToken> tokens) {
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
    }

    private String startImageServer(String path, byte[] imageBytes, String contentType) throws IOException {
        if (imageServer == null) {
            imageServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            imageServer.start();
        }
        imageServer.createContext(
                "/" + path,
                exchange -> {
                    exchange.getResponseHeaders().add("Content-Type", contentType);
                    exchange.sendResponseHeaders(200, imageBytes.length);
                    exchange.getResponseBody().write(imageBytes);
                    exchange.close();
                });
        return "http://localhost:" + imageServer.getAddress().getPort() + "/" + path;
    }
}
