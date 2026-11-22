package com.rongwang.auth.web;

import com.rongwang.auth.model.UserAccount;
import com.rongwang.auth.model.UserRole;
import com.rongwang.auth.model.UserStatus;
import com.rongwang.auth.repository.UserAccountRepository;
import com.rongwang.auth.service.AuthService;
import com.rongwang.auth.session.SessionAuthInterceptor;
import com.rongwang.common.exception.GlobalExceptionHandler;
import com.rongwang.config.WebMvcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        GlobalExceptionHandler.class,
        SessionAuthInterceptor.class,
        WebMvcConfig.class,
        AuthService.class,
        AuthControllerWebTests.TestAuthConfiguration.class
})
class AuthControllerWebTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginCreatesSessionAndCurrentUserCanReadIt() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"student001","password":"StudentPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(get("/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("7"))
                .andExpect(jsonPath("$.data.username").value("student001"));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/v1/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        org.assertj.core.api.Assertions.assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void unauthenticatedRequestUsesUnifiedErrorResponse() throws Exception {
        mockMvc.perform(get("/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void validationErrorUsesUnifiedErrorResponse() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"StudentPassword123!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请输入账号"));
    }

    @Test
    void malformedJsonUsesUnifiedErrorResponse() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value("请求体格式不正确"));
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"student001","password":"StudentPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestAuthConfiguration {

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        UserAccountRepository userAccountRepository(PasswordEncoder passwordEncoder) {
            UserAccount account = new UserAccount();
            account.setId(7L);
            account.setUsername("student001");
            account.setPasswordHash(passwordEncoder.encode("StudentPassword123!"));
            account.setDisplayName("陈同学");
            account.setStudentNo("20260001");
            account.setRole(UserRole.STUDENT);
            account.setStatus(UserStatus.ACTIVE);
            return username -> "student001".equals(username)
                    ? Optional.of(account)
                    : Optional.empty();
        }
    }
}
