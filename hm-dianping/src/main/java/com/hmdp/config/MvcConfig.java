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
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/code", "/user/login", "/user/refresh", "/user/logout", "/blog/hot")
                .order(1);

        registry.addInterceptor(new RefreshTokenInterceptor(jwtUtil)).addPathPatterns("/**").order(0);
    }
}
