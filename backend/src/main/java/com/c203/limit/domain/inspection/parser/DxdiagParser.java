package com.c203.limit.domain.inspection.parser;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * DxDiag 진단 파일의 확장자·MIME 타입·내용을 보고 TXT(Save All Information)와 XML(dxdiag /x) 포맷을
 * 감지해 알맞은 파서로 위임한다.
 */
@Component
public class DxdiagParser {

    private final DxdiagTxtParser dxdiagTxtParser;
    private final DxdiagXmlParser dxdiagXmlParser;

    public DxdiagParser(DxdiagTxtParser dxdiagTxtParser, DxdiagXmlParser dxdiagXmlParser) {
        this.dxdiagTxtParser = dxdiagTxtParser;
        this.dxdiagXmlParser = dxdiagXmlParser;
    }

    public DxdiagParseResult parse(byte[] bytes, String s3Key, String mimeType) {
        try {
            return switch (detectFormat(bytes, s3Key, mimeType)) {
                case XML -> dxdiagXmlParser.parse(bytes);
                case TXT -> dxdiagTxtParser.parse(bytes);
            };
        } catch (DxdiagParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DxdiagParseException("Failed to parse DxDiag file", exception);
        }
    }

    private Format detectFormat(byte[] bytes, String s3Key, String mimeType) {
        String lowerKey = s3Key == null ? "" : s3Key.toLowerCase(Locale.ROOT);
        if (lowerKey.endsWith(".xml")) {
            return Format.XML;
        }
        if (lowerKey.endsWith(".txt")) {
            return Format.TXT;
        }

        String lowerMimeType = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (lowerMimeType.contains("xml")) {
            return Format.XML;
        }
        if (lowerMimeType.contains("text/plain")) {
            return Format.TXT;
        }

        return sniffContent(bytes);
    }

    private Format sniffContent(byte[] bytes) {
        int length = Math.min(bytes.length, 200);
        String head = new String(bytes, 0, length, StandardCharsets.UTF_8).stripLeading();
        return head.startsWith("<") ? Format.XML : Format.TXT;
    }

    private enum Format {
        TXT,
        XML
    }
}
