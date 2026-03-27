package com.caseflow.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Minimal security configuration for CaseFlow.
 *
 * Auth strategy: HTTP Basic with in-memory users (dev-friendly).
 * Replace InMemoryUserDetailsManager with a real UserDetailsService
 * backed by the users table when production auth is needed.
 *
 * Roles:
 *   ADMIN  — full access including destructive/admin endpoints
 *   AGENT  — operational read/write (most POST/PUT/PATCH)
 *   VIEWER — read-only (GET only)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health/**",
            "/actuator/info"
    };

    @Value("${caseflow.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String[] allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // Public paths
                        .requestMatchers(PUBLIC_PATHS).permitAll()

                        // GET: require at least VIEWER
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("VIEWER", "AGENT", "ADMIN")

                        // Operational mutations: require AGENT or ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole("AGENT", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyRole("AGENT", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasAnyRole("AGENT", "ADMIN")

                        // Destructive ops: require ADMIN
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")

                        // Everything else: authenticated
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        // In-memory users for dev. Replace with DB-backed UserDetailsService for production.
        return new InMemoryUserDetailsManager(
                User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("admin123"))
                        .roles("ADMIN")
                        .build(),
                User.builder()
                        .username("agent")
                        .password(passwordEncoder.encode("agent123"))
                        .roles("AGENT")
                        .build(),
                User.builder()
                        .username("viewer")
                        .password(passwordEncoder.encode("viewer123"))
                        .roles("VIEWER")
                        .build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
