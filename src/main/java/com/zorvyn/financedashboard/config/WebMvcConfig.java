package com.zorvyn.financedashboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ============================================================================
 * WEB MVC CONFIGURATION — Interceptor Registration
 * ============================================================================
 *
 * Registers the RateLimiterInterceptor on SPECIFIC URL patterns.
 *
 * We apply rate limiting ONLY to dashboard summary endpoints because:
 *   1. These endpoints execute expensive aggregate queries (SUM, GROUP BY)
 *   2. They're the most likely target for scraping or abuse
 *   3. Other endpoints (CRUD operations) are naturally throttled by
 *      user interaction patterns
 *
 * If we applied rate limiting globally, legitimate API integrations
 * (e.g., a batch import of transactions) would be unfairly throttled.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimiterInterceptor rateLimiterInterceptor;

    public WebMvcConfig(RateLimiterInterceptor rateLimiterInterceptor) {
        this.rateLimiterInterceptor = rateLimiterInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // Apply rate limiting only to dashboard summary endpoints
        registry.addInterceptor(rateLimiterInterceptor)
                .addPathPatterns("/dashboard/**");
    }
}
