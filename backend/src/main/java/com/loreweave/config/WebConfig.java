package com.loreweave.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Applies the turn rate limiter to the one expensive, LLM-backed endpoint. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimit;

    public WebConfig(RateLimitInterceptor rateLimit) { this.rateLimit = rateLimit; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimit).addPathPatterns("/api/sessions/*/turns");
    }
}
