package com.rongwang.auth.repository;

import com.rongwang.auth.model.UserAccount;

import java.util.Optional;

public interface UserAccountRepository {

    Optional<UserAccount> findByUsername(String username);
}
