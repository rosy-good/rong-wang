package com.rongwang.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PasswordHashTool {

    private PasswordHashTool() {
    }

    public static void main(String[] args) {
        if (args.length != 1 || args[0].isBlank()) {
            throw new IllegalArgumentException("请通过程序参数传入一个非空密码");
        }
        System.out.println(new BCryptPasswordEncoder(12).encode(args[0]));
    }
}
