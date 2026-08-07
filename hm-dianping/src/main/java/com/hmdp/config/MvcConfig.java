package com.hmdp.config;

import com.hmdp.utils.JwtUtil;
import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    private final JwtUtil jwtUtil;

    public MvcConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/code",
                        "/user/login",
                        "/user/refresh",   // 续约在 access 过期时调用，只靠 refreshToken（cookie），不能要求已登录
                        "/user/logout",
                        "/blog/hot",
                        "/shop-type/**",
                        "/shop/**",
                        "/upload/**",
                        "/voucher/**",
                        "/kb/**",
                        "/api/deepseek-proxy/**",
                        "/debug/**",
                        "/qdrant-admin.html",
                        "/api/qdrant/admin/**"
                        ).order(1);
        // accessToken 拦截器（无状态 JWT 验签）
        registry.addInterceptor(new RefreshTokenInterceptor(jwtUtil)).addPathPatterns("/**").order(0);

    }
}
