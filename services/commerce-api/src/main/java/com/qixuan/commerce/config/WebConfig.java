package com.qixuan.commerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminInterceptor adminIterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, AdminInterceptor adminInterceptor,
            RateLimitInterceptor rateLimitInterceptor) {
        this.authInterceptor = authInterceptor;
        this.adminIterceptor = adminInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 限流注册在鉴权之前：被限掉的请求连认证都不用做，省时间
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");

        registry.addInterceptor(authInterceptor)
                // 所有接口默认都要登录
                .addPathPatterns("/api/**")
                // 例外：不需要身份就能访问的
                .excludePathPatterns(
                        "/api/auth/**", // 注册、登录本身不能要求登录
                        "/api/health", // 健康检查
                        "/api/products/**", // 买家浏览商品
                        "/api/categories/**" // 买家浏览分类
                );
        registry.addInterceptor(adminIterceptor).addPathPatterns("/api/admin/**");
    }
}
