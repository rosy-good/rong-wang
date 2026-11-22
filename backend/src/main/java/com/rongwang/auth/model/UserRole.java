package com.rongwang.auth.model;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum UserRole {
    TEACHER("TEACHER"),
    STUDENT("STUDENT");

    @EnumValue
    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
