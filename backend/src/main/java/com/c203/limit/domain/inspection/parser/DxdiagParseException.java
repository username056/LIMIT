package com.c203.limit.domain.inspection.parser;

/** DxDiag.xml이 구조상 해석 불가능할 때(루트 태그 불일치, XML 파싱 오류 등) 던진다. */
public class DxdiagParseException extends RuntimeException {

    public DxdiagParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public DxdiagParseException(String message) {
        super(message);
    }
}
