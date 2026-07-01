package com.minibank.auth.dto;

public record AuthResponse(String token, String email, String displayName) {
}
