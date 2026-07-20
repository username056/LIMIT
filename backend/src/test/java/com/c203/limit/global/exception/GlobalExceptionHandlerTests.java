package com.c203.limit.global.exception;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setTraceId() {
        MDC.put("traceId", "trace-test");
    }

    @AfterEach
    void clearTraceId() {
        MDC.clear();
    }

    @Test
    void businessExceptionUsesErrorCodeWhenCustomMessageIsBlank() {
        BusinessException exception = new BusinessException(ErrorCode.INVALID_INPUT_VALUE, " ");

        var response = handler.handleBusiness(exception);

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("CMN003");
        assertThat(response.getBody().error().message()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());
        assertThat(response.getBody().traceId()).isEqualTo("trace-test");
    }

    @Test
    void validationResponseDoesNotExposeRejectedValue() throws NoSuchMethodException {
        ValidationRequest request = new ValidationRequest("secret-value");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "request");
        bindingResult.rejectValue("password", "NotBlank", "비밀번호를 입력해 주세요.");
        MethodParameter parameter = new MethodParameter(
                TestMethodHolder.class.getDeclaredMethod("validate", ValidationRequest.class), 0);
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(parameter, bindingResult);

        var response = handler.handleValidation(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().fieldErrors())
                .containsExactly(new com.c203.limit.global.response.ApiErrorResponse.FieldError(
                        "password", "비밀번호를 입력해 주세요."));
        assertThat(response.getBody().toString()).doesNotContain("secret-value");
    }

    @Test
    void malformedRequestBodyUsesCommonErrorResponse() {
        var response = handler.handleUnreadableBody(mock(HttpMessageNotReadableException.class));

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("CMN003");
        assertThat(response.getBody().traceId()).isEqualTo("trace-test");
    }

    @Test
    void invalidOrMissingRequestValueUsesBadRequestContract() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                TestMethodHolder.class.getDeclaredMethod("validate", ValidationRequest.class), 0);
        var typeMismatch = new MethodArgumentTypeMismatchException(
                "invalid", Long.class, "productId", parameter, null);

        var invalidResponse = handler.handleTypeMismatch(typeMismatch);
        var missingResponse = handler.handleMissingRequiredValue(
                new MissingServletRequestParameterException("productId", "long"));

        assertThat(invalidResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidResponse.getBody().error().code()).isEqualTo("CMN004");
        assertThat(missingResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingResponse.getBody().error().code()).isEqualTo("CMN005");
    }

    @Test
    void routingExceptionsUseCommonErrorContract() {
        var notFoundResponse = handler.handleNotFound(mock(NoResourceFoundException.class));
        var methodNotAllowedResponse = handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("PATCH"));

        assertThat(notFoundResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFoundResponse.getBody()).isNull();
        assertThat(methodNotAllowedResponse.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(methodNotAllowedResponse.getBody()).isNull();
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalMessage() {
        var response = handler.handleUnexpected(new IllegalStateException("database-host-secret"));

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message())
                .isEqualTo(ErrorCode.INTERNAL_ERROR.getMessage());
        assertThat(response.getBody().toString()).doesNotContain("database-host-secret");
    }

    private record ValidationRequest(String password) {}

    private static class TestMethodHolder {
        @SuppressWarnings("unused")
        void validate(ValidationRequest request) {}
    }
}
