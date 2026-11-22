package com.rongwang.auth.service;

import com.rongwang.auth.model.UserAccount;
import com.rongwang.auth.model.UserRole;
import com.rongwang.auth.model.UserStatus;
import com.rongwang.auth.repository.UserAccountRepository;
import com.rongwang.auth.session.LoginUser;
import com.rongwang.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTests {

    private static final String RAW_PASSWORD = "TestPassword123!";

    private UserAccount activeAccount;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        activeAccount = account(UserStatus.ACTIVE, passwordEncoder.encode(RAW_PASSWORD));
        UserAccountRepository repository = username ->
                "teacher001".equals(username) ? Optional.of(activeAccount) : Optional.empty();
        authService = new AuthService(repository, passwordEncoder);
    }

    @Test
    void activeAccountWithCorrectPasswordCanLogin() {
        LoginUser user = authService.login(" teacher001 ", RAW_PASSWORD);

        assertThat(user.id()).isEqualTo(1L);
        assertThat(user.role()).isEqualTo(UserRole.TEACHER);
        assertThat(user.displayName()).isEqualTo("林老师");
    }

    @Test
    void wrongPasswordIsRejected() {
        assertThatThrownBy(() -> authService.login("teacher001", "wrong-password"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(401);
                    assertThat(exception.getCode()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    @Test
    void unknownAccountIsRejected() {
        assertThatThrownBy(() -> authService.login("missing", RAW_PASSWORD))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void disabledAccountCannotLogin() {
        activeAccount.setStatus(UserStatus.DISABLED);

        assertThatThrownBy(() -> authService.login("teacher001", RAW_PASSWORD))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(403);
                    assertThat(exception.getCode()).isEqualTo("ACCOUNT_DISABLED");
                });
    }

    private UserAccount account(UserStatus status, String passwordHash) {
        UserAccount account = new UserAccount();
        account.setId(1L);
        account.setUsername("teacher001");
        account.setPasswordHash(passwordHash);
        account.setDisplayName("林老师");
        account.setRole(UserRole.TEACHER);
        account.setStatus(status);
        return account;
    }
}
