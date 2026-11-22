package com.rongwang.submission.web;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.*;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SubmissionArrivalFilter extends OncePerRequestFilter {
    public static final String RECEIVED_AT="RW_SUBMISSION_RECEIVED_AT";
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        if("POST".equals(request.getMethod()) &&
                request.getRequestURI().matches(".*/v1/student/tasks/[0-9]+/submissions"))
            request.setAttribute(RECEIVED_AT,LocalDateTime.now(ZONE));
        chain.doFilter(request,response);
    }
}

