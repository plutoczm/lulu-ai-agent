package com.lulu.luluaiagent.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthWebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final UsageLimitInterceptor usageLimitInterceptor;

    public AuthWebConfig(
            AuthInterceptor authInterceptor,
            UsageLimitInterceptor usageLimitInterceptor) {
        this.authInterceptor = authInterceptor;
        this.usageLimitInterceptor = usageLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/register",
                        "/auth/login",
                        "/health",
                        "/channel/coach/**",
                        "/error");

        registry.addInterceptor(usageLimitInterceptor)
                .addPathPatterns(
                        "/ai/memory/**",
                        "/ai/coach/**",
                        "/ai/manus/**",
                        "/ai/tools/**");
    }
}
