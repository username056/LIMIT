package com.c203.limit.domain.inspection.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DxdiagParserTests {

    private final DxdiagParser parser = new DxdiagParser(new DxdiagTxtParser(), new DxdiagXmlParser());

    private static final String MINIMAL_XML =
            """
            <DxDiag>
              <SystemInformation>
                <Processor>XML CPU</Processor>
              </SystemInformation>
            </DxDiag>
            """;

    private static final String MINIMAL_TXT =
            """
            ------------------
            System Information
            ------------------
                  Processor: TXT CPU
            """;

    @Test
    void detectsXmlByFileExtension() {
        DxdiagParseResult result = parser.parse(bytes(MINIMAL_XML), "evidence/dxdiag.xml", "application/octet-stream");

        assertThat(result.cpu()).isEqualTo("XML CPU");
    }

    @Test
    void detectsTxtByFileExtension() {
        DxdiagParseResult result = parser.parse(bytes(MINIMAL_TXT), "evidence/dxdiag.txt", "application/octet-stream");

        assertThat(result.cpu()).isEqualTo("TXT CPU");
    }

    @Test
    void fallsBackToMimeTypeWhenExtensionIsMissing() {
        DxdiagParseResult xmlResult = parser.parse(bytes(MINIMAL_XML), "evidence/dxdiag", "text/xml");
        DxdiagParseResult txtResult = parser.parse(bytes(MINIMAL_TXT), "evidence/dxdiag", "text/plain");

        assertThat(xmlResult.cpu()).isEqualTo("XML CPU");
        assertThat(txtResult.cpu()).isEqualTo("TXT CPU");
    }

    @Test
    void fallsBackToContentSniffingWhenExtensionAndMimeTypeAreInconclusive() {
        DxdiagParseResult xmlResult =
                parser.parse(bytes(MINIMAL_XML), "evidence/dxdiag", "application/octet-stream");
        DxdiagParseResult txtResult =
                parser.parse(bytes(MINIMAL_TXT), "evidence/dxdiag", "application/octet-stream");

        assertThat(xmlResult.cpu()).isEqualTo("XML CPU");
        assertThat(txtResult.cpu()).isEqualTo("TXT CPU");
    }

    @Test
    void propagatesParseExceptionWhenDetectedXmlIsMalformed() {
        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        bytes("<DxDiag><Unclosed>"), "evidence/dxdiag.xml", "text/xml"))
                .isInstanceOf(DxdiagParseException.class);
    }

    private byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
