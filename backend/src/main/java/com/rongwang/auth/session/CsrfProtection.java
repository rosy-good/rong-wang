package com.rongwang.auth.session;

import com.rongwang.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.UUID;

public class CsrfProtection implements HandlerInterceptor {
    private static final String KEY = "RW_CSRF_TOKEN";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    public static String token(HttpSession session) {
        synchronized (session) {
            String token = (String) session.getAttribute(KEY);
            if (token == null) {
                token = UUID.randomUUID().toString();
                session.setAttribute(KEY, token);
            }
            return token;
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (SAFE_METHODS.contains(request.getMethod())) return true;
        HttpSession session = request.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute(KEY);
        String supplied = request.getHeader("X-CSRF-Token");
        if (expected == null || supplied == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CSRF_INVALID", "请刷新登录状态后重试");
        }
        return true;
    }
}
