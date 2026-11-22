package com.rongwang.auth.session;

import com.rongwang.auth.model.UserRole;
import com.rongwang.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionAuthInterceptorTests {

    private final SessionAuthInterceptor interceptor = new SessionAuthInterceptor();

    @Test
    void unauthenticatedRequestIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new Object()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(401);
                    assertThat(exception.getCode()).isEqualTo("UNAUTHENTICATED");
                });
    }

    @Test
    void authenticatedRequestCanContinue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(
                SessionKeys.LOGIN_USER,
                new LoginUser(1L, "teacher001", "林老师", UserRole.TEACHER));

        boolean allowed = interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new Object());

        assertThat(allowed).isTrue();
    }
}
