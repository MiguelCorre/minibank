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

import com.minibank.auth.AuthService.TokenPair;
import com.minibank.auth.dto.AuthResponse;
import com.minibank.auth.dto.LoginRequest;
import com.minibank.auth.dto.RefreshRequest;
import com.minibank.auth.dto.RegisterRequest;
import com.minibank.auth.dto.UserInfo;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration, login and refresh-token rotation")
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
        return toResponse(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return toResponse(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    UserInfo me(@AuthenticationPrincipal Jwt jwt) {
        return new UserInfo(jwt.getSubject(), jwt.getClaimAsString("name"));
    }

    private static AuthResponse toResponse(TokenPair pair) {
        return new AuthResponse(pair.accessToken(), pair.refreshToken(),
                pair.user().getEmail(), pair.user().getDisplayName());
    }
}
