package com.nibash.auth;

import com.nibash.common.ApiException;
import com.nibash.user.User;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Resolves the caller established by {@link TokenAuthFilter}. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<User> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static User require() {
        return find().orElseThrow(() ->
                ApiException.unauthorized("Authentication credentials were not provided."));
    }
}
