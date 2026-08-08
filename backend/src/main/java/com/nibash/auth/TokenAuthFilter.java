package com.nibash.auth;

import com.nibash.user.Roles;
import com.nibash.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Step 1 of the request lifecycle (spec §3): resolve {@code Authorization: Token <key>} into the
 * caller, then populate the SecurityContext with authorities derived from role + back-office flags.
 *
 * <p>Deliberately <b>not</b> a {@code @Component} and <b>not</b> {@code @Transactional}:
 * <ul>
 *   <li>{@code @Transactional} would make Spring proxy this class with CGLIB, and the proxy is
 *       instantiated without running {@code GenericFilterBean}'s field initialisers — leaving
 *       {@code logger} null and blowing up when Tomcat starts the filter.</li>
 *   <li>{@code @Component} would additionally register it with the servlet container, so it would
 *       run twice per request. It is wired into the security chain by {@code SecurityConfig}.</li>
 * </ul>
 * The token lookup uses an {@code @EntityGraph} to fetch the user eagerly, so no transaction is
 * needed here.
 */
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Token ";

    private final AuthTokenRepository tokens;

    public TokenAuthFilter(AuthTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String key = header.substring(PREFIX.length()).trim();
            tokens.findByKey(key)
                    .map(AuthToken::getUser)
                    .filter(User::isActive)
                    .ifPresent(user -> {
                        var auth = new UsernamePasswordAuthenticationToken(user, null, authorities(user));
                        auth.setDetails(user.getId());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }

        chain.doFilter(request, response);
    }

    private List<GrantedAuthority> authorities(User user) {
        List<GrantedAuthority> granted = new ArrayList<>();
        granted.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase()));
        if (user.isBackOffice()) {
            granted.add(new SimpleGrantedAuthority("ROLE_BACKOFFICE"));
        }
        if (user.isBackOffice() || Roles.ADMIN.equals(user.getRole()) || Roles.COMMITTEE.equals(user.getRole())) {
            // The CommitteeOrAdmin policy (spec §5) collapses to this authority.
            granted.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
        }
        return granted;
    }
}
