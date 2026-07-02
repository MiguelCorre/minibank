package com.minibank.auth;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.common.error.InvalidCredentialsException;

/**
 * Resolves the id of the customer behind the current bearer token.
 */
@Service
public class CurrentUserService {

    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UUID requireCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return users.findByEmail(jwt.getSubject())
                    .map(User::getId)
                    .orElseThrow(InvalidCredentialsException::new);
        }
        throw new InvalidCredentialsException();
    }
}
