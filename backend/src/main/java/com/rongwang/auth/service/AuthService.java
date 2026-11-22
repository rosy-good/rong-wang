package com.rongwang.auth.service;

import com.rongwang.auth.model.UserAccount;
import com.rongwang.auth.model.UserStatus;
import com.rongwang.auth.repository.UserAccountRepository;
import com.rongwang.auth.session.LoginUser;
import com.rongwang.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginUser login(String username, String rawPassword) {
        UserAccount account = userAccountRepository.findByUsername(username.trim())
                .orElseThrow(() -> invalidCredentials());

        if (account.getStatus() == UserStatus.DISABLED) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_DISABLED",
                    "该账号已禁用，请联系平台维护者");
        }
        if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            throw invalidCredentials();
        }
        return LoginUser.from(account);
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "账号或密码不正确");
    }
}
