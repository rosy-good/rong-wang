package com.rongwang.auth.session;

import com.rongwang.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

public final class CurrentUser {
    private CurrentUser() { }

    public static LoginUser require(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(SessionKeys.LOGIN_USER) instanceof LoginUser user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录");
        }
        return user;
    }
}
