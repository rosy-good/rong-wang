package com.rongwang.config;

import com.rongwang.auth.session.SessionAuthInterceptor;
import com.rongwang.auth.session.CsrfProtection;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SessionAuthInterceptor sessionAuthInterceptor;

    public WebMvcConfig(SessionAuthInterceptor sessionAuthInterceptor) {
        this.sessionAuthInterceptor = sessionAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionAuthInterceptor)
                .addPathPatterns("/v1/**")
                .excludePathPatterns("/v1/auth/login");
        registry.addInterceptor(new CsrfProtection())
                .addPathPatterns("/v1/teacher/**", "/v1/student/**");
    }
}
