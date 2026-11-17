package com.qixuan.commerce.config;

import com.qixuan.commerce.common.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 从 Authorization: Bearer <token> 里解析出 userId，放进请求属性。
 * 解析失败直接抛 401，由 GlobalExceptionHandler 统一转成 JSON。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public AuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        String token = header.substring(BEARER_PREFIX.length());
        Long userId = tokenService.verify(token);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        request.setAttribute("userId", userId);
        return true;
    }
}
