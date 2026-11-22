package com.rongwang.auth.web;

import com.rongwang.auth.dto.CurrentUserResponse;
import com.rongwang.auth.dto.LoginRequest;
import com.rongwang.auth.service.AuthService;
import com.rongwang.auth.session.LoginUser;
import com.rongwang.auth.session.SessionKeys;
import com.rongwang.auth.session.CsrfProtection;
import com.rongwang.common.api.ApiResponse;
import com.rongwang.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<CurrentUserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        LoginUser loginUser = authService.login(request.username(), request.password());
        HttpSession oldSession = servletRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = servletRequest.getSession(true);
        session.setAttribute(SessionKeys.LOGIN_USER, loginUser);
        return ApiResponse.success(CurrentUserResponse.from(loginUser));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> currentUser(HttpServletRequest request) {
        return ApiResponse.success(CurrentUserResponse.from(requireLoginUser(request)));
    }

    @GetMapping("/csrf")
    public ApiResponse<java.util.Map<String, String>> csrf(HttpServletRequest request) {
        requireLoginUser(request);
        return ApiResponse.success(java.util.Map.of("token", CsrfProtection.token(request.getSession(false))));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.success(null);
    }

    private LoginUser requireLoginUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(SessionKeys.LOGIN_USER) instanceof LoginUser user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录");
        }
        return user;
    }
}
