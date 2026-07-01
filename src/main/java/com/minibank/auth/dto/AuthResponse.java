package com.minibank.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, String email, String displayName) {
}
