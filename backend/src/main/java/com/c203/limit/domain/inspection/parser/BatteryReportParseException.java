package com.c203.limit.domain.inspection.parser;

/** 배터리 리포트 HTML이 구조상 해석 불가능할 때(마크업 손상 등) 던진다. */
public class BatteryReportParseException extends RuntimeException {

    public BatteryReportParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
