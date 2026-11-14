package com.yanxai.tracefolio.config;

import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        return error(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                "请求参数不完整或格式不正确。",
                "按接口契约补齐必填字段后重试。",
                detail
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> handleStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return error(
                status,
                status.name(),
                exception.getReason() == null ? status.getReasonPhrase() : exception.getReason(),
                null,
                null
        );
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    ResponseEntity<ApiErrorResponse> handleEmptyResult() {
        return error(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "请求的资源不存在。",
                null,
                null
        );
    }

    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<ApiErrorResponse> handleResourceAccess(ResourceAccessException exception) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DEPENDENCY_UNAVAILABLE",
                "外部模型服务暂时不可用。",
                "确认 Ollama 已启动、DeepSeek 网络可访问后重试。",
                exception.getMessage()
        );
    }

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<ApiErrorResponse> handleRestClient(RestClientResponseException exception) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "MODEL_REQUEST_FAILED",
                "外部模型服务返回错误，本次请求未完成。",
                "如果错误中提示模型不存在，请先执行 ollama pull bge-m3；如果是 DeepSeek 鉴权错误，请检查 DEEPSEEK_API_KEY。",
                responseBodyOrMessage(exception)
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException exception) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "RUNTIME_NOT_READY",
                "运行环境还没有准备好。",
                "检查 Ollama 模型、DeepSeek API Key 和数据库连接后重试。",
                exception.getMessage()
        );
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            String action,
            String detail
    ) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(Instant.now(), status.value(), code, message, action, detail));
    }

    private static String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private static String responseBodyOrMessage(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return exception.getMessage();
        }
        return body;
    }

    public record ApiErrorResponse(
            Instant timestamp,
            int status,
            String code,
            String message,
            String action,
            String detail
    ) {
    }
}
