package com.nibash.config;

import com.nibash.auth.AuthTokenRepository;
import com.nibash.auth.TokenAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;   // Spring Boot 4 ships Jackson 3 (tools.jackson.*)

@Configuration
public class SecurityConfig {

    private final TokenAuthFilter tokenAuthFilter;
    private final ObjectMapper objectMapper;

    @Value("${nibash.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * The filter is constructed here rather than component-scanned, so the servlet container never
     * auto-registers it (see the note on {@link TokenAuthFilter}).
     */
    public SecurityConfig(AuthTokenRepository tokens, ObjectMapper objectMapper) {
        this.tokenAuthFilter = new TokenAuthFilter(tokens);
        this.objectMapper = objectMapper;
    }

    /** Spec §2.2: Django PBKDF2 → BCrypt. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        // AllowAny endpoints (spec §5)
                        .requestMatchers("/api/auth/login/", "/api/auth/signup/").permitAll()
                        .requestMatchers("/api/intercom/webhook", "/api/ml/price-estimate").permitAll()
                        .requestMatchers("/media/**", "/actuator/health").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                write(res, HttpServletResponse.SC_UNAUTHORIZED,
                                        Map.of("detail", "Authentication credentials were not provided.")))
                        .accessDeniedHandler((req, res, e) ->
                                write(res, HttpServletResponse.SC_FORBIDDEN,
                                        Map.of("detail", "You do not have permission to perform this action."))))
                .addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void write(HttpServletResponse response, int status, Map<String, Object> body) {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try {
            objectMapper.writeValue(response.getOutputStream(), body);
        } catch (Exception ignored) {
            // the client has gone away; nothing useful to do
        }
    }

    /**
     * Dev only: the Vite dev server on :5173 calls the API cross-origin (spec §13.2).
     * In production Nginx fronts everything on one origin, so this never fires.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
