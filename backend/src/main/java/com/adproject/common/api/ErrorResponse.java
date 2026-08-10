package com.adproject.common.api;

import java.util.Map;

public record ErrorResponse(ErrorDetail error) {
    public record ErrorDetail(String code, String message, Map<String, String> fieldErrors, String requestId) {}

    public static ErrorResponse of(String code, String message, Map<String, String> fieldErrors, String requestId) {
        return new ErrorResponse(new ErrorDetail(code, message, fieldErrors, requestId));
    }
}
