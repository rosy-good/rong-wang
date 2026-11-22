package com.rongwang.auth.dto;

import com.rongwang.auth.session.LoginUser;

public record CurrentUserResponse(
        String id,
        String username,
        String displayName,
        String role) {

    public static CurrentUserResponse from(LoginUser user) {
        return new CurrentUserResponse(
                user.id().toString(),
                user.username(),
                user.displayName(),
                user.role().getValue());
    }
}
