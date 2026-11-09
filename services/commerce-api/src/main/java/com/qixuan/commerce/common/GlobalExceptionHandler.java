
package com.qixuan.commerce.common;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        // 构建统一错误响应
        private ApiError error(
                        String code,
                        String message,
                        HttpServletRequest request) {

                return new ApiError(
                                code,
                                message,
                                request.getRequestURI(),
                                Instant.now());
        }

        // 处理业务 HTTP 异常（400、404、409 等）
        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<ApiError> handleStatus(
                        ResponseStatusException ex,
                        HttpServletRequest request) {

                int status = ex.getStatusCode().value();

                String code = switch (status) {
                        case 400 -> "BAD_REQUEST";
                        case 404 -> "NOT_FOUND";
                        case 409 -> "CONFLICT";
                        default -> "HTTP_ERROR";
                };

                String message = ex.getReason() == null
                                ? "请求处理失败"
                                : ex.getReason();

                return ResponseEntity
                                .status(ex.getStatusCode())
                                .body(error(code, message, request));
        }

        // 处理 @Valid 参数校验失败
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiError> handleValidation(
                        MethodArgumentNotValidException ex,
                        HttpServletRequest request) {

                String message = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(fieldError -> fieldError.getField() + "：" +
                                                fieldError.getDefaultMessage())
                                .distinct()
                                .collect(Collectors.joining("；"));

                if (message.isBlank()) {
                        message = "请求参数不合法";
                }

                return ResponseEntity
                                .badRequest()
                                .body(error(
                                                "VALIDATION_ERROR",
                                                message,
                                                request));
        }

        // 处理 JSON 格式错误
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiError> handleJsonError(
                        HttpServletRequest request) {

                return ResponseEntity
                                .badRequest()
                                .body(error(
                                                "INVALID_JSON",
                                                "请求 JSON 格式错误",
                                                request));
        }

        // 处理路径参数类型错误，例如 /categories/abc
        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ApiError> handleTypeMismatch(
                        HttpServletRequest request) {

                return ResponseEntity
                                .badRequest()
                                .body(error(
                                                "INVALID_PARAMETER",
                                                "请求参数类型不正确",
                                                request));
        }

        // 处理不存在的静态资源或路径
        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ApiError> handleNotFound(
                        HttpServletRequest request) {

                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(error(
                                                "NOT_FOUND",
                                                "请求资源不存在",
                                                request));
        }

        // 处理不支持的 HTTP 方法
        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ApiError> handleMethodNotAllowed(
                        HttpServletRequest request) {

                return ResponseEntity
                                .status(HttpStatus.METHOD_NOT_ALLOWED)
                                .body(error(
                                                "METHOD_NOT_ALLOWED",
                                                "请求方法不受支持",
                                                request));
        }

        // 处理未预期的服务器异常
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiError> handleUnexpected(
                        Exception ex,
                        HttpServletRequest request) {

                log.error("Unhandled API exception", ex);

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(error(
                                                "INTERNAL_ERROR",
                                                "服务器内部错误，请稍后重试",
                                                request));
        }

        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<ApiError> handleMissingParameter(
                        MissingServletRequestParameterException ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .badRequest()
                                .body(error(
                                                "MISSING_PARAMETER",
                                                "缺少必填请求参数：" + ex.getParameterName(),
                                                request));
        }
}
