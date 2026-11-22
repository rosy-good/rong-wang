package com.rongwang.auth.session;

import com.rongwang.auth.model.UserAccount;
import com.rongwang.auth.model.UserRole;

import java.io.Serializable;

public record LoginUser(
        Long id,
        String username,
        String displayName,
        UserRole role) implements Serializable {

    public static LoginUser from(UserAccount account) {
        return new LoginUser(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                account.getRole());
    }
}
