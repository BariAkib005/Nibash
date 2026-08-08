package com.nibash.auth;

import com.nibash.auth.dto.AuthDtos.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Spec §4.2. Trailing slashes are part of the contract — Spring 6+ no longer matches them
 * automatically, so the mappings carry them explicitly.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login/")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/signup/")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/logout/")
    public Map<String, String> logout() {
        authService.logout(CurrentUser.require());
        return Map.of("detail", "Logged out.");
    }

    @GetMapping("/me/")
    public SessionResponse me() {
        return authService.me(CurrentUser.require());
    }
}
