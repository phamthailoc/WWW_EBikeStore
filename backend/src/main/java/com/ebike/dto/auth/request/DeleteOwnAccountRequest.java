package com.ebike.dto.auth.request;

public record DeleteOwnAccountRequest(
    String password,
    String confirmEmail
) {
}
