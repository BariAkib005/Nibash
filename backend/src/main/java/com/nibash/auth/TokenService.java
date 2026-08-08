package com.nibash.auth;

import com.nibash.user.User;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues and revokes the opaque 40-hex tokens described in spec §4.5. */
@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthTokenRepository tokens;

    public TokenService(AuthTokenRepository tokens) {
        this.tokens = tokens;
    }

    /** "Get or create" semantics on login — one active token per user. */
    @Transactional
    public String getOrCreate(User user) {
        return tokens.findByUserId(user.getId())
                .map(AuthToken::getKey)
                .orElseGet(() -> tokens.save(new AuthToken(generateKey(), user)).getKey());
    }

    /** Logout deletes *all* tokens for the caller (spec §4.2). */
    @Transactional
    public void revokeAll(Long userId) {
        tokens.deleteByUserId(userId);
    }

    /** Password change revokes the old token and issues a new one (spec §4.3). */
    @Transactional
    public String rotate(User user) {
        tokens.deleteByUserId(user.getId());
        tokens.flush();
        return tokens.save(new AuthToken(generateKey(), user)).getKey();
    }

    /** 20 random bytes rendered as 40 hex characters. */
    private String generateKey() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
