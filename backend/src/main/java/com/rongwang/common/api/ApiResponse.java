package com.rongwang.common.api;

public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", "操作成功", data);
    }
}
