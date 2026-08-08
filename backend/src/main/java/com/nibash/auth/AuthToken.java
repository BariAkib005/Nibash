package com.nibash.auth;

import com.nibash.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Opaque 40-hex-char token, one active per user (spec §4.5). */
@Entity
@Table(name = "auth_tokens")
public class AuthToken {

    @Id
    @Column(name = "`key`", length = 40, nullable = false)
    private String key;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime created;

    public AuthToken() {
    }

    public AuthToken(String key, User user) {
        this.key = key;
        this.user = user;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDateTime getCreated() { return created; }
}
