package com.minibank.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.auth.dto.AuthResponse;
import com.minibank.auth.dto.LoginRequest;
import com.minibank.auth.dto.RegisterRequest;
import com.minibank.auth.dto.UserInfo;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserInfo register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.email(), request.password(), request.displayName());
        return new UserInfo(user.getEmail(), user.getDisplayName());
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.email(), request.password());
        User user = authService.findByEmail(request.email().toLowerCase().strip());
        return new AuthResponse(token, user.getEmail(), user.getDisplayName());
    }

    @GetMapping("/me")
    UserInfo me(@AuthenticationPrincipal Jwt jwt) {
        return new UserInfo(jwt.getSubject(), jwt.getClaimAsString("name"));
    }
}
