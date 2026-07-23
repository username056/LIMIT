package com.c203.limit.domain.auth.service;

public record SessionResult<T>(T body, String refreshToken) {}
