package com.qixuan.commerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminInterceptor adminIterceptor;

    public WebConfig(AuthInterceptor authInterceptor, AdminInterceptor adminInterceptor) {
        this.authInterceptor = authInterceptor;
        this.adminIterceptor = adminInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
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
