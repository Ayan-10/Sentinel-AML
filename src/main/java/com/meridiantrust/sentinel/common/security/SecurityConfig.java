package com.meridiantrust.sentinel.common.security;

import com.meridiantrust.sentinel.common.config.SentinelProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * NFR (Security): role-based access control enforced at the API layer.
 *
 * <p>Two independent layers, deliberately:
 * <ol>
 *   <li><b>URL-level</b> rules here, which catch anything reachable over HTTP.</li>
 *   <li><b>Method-level</b> {@code @PreAuthorize} on <em>service</em> methods, which
 *       continue to hold when a service is invoked from a scheduler, a message
 *       listener or a future controller that nobody remembered to secure.</li>
 * </ol>
 * A UI-only check — the failure the brief explicitly calls out — is impossible
 * here because the UI never participates in the decision.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * COMPLIANCE_ADMIN implies SENIOR_ANALYST implies ANALYST, so a senior
     * analyst need not be granted every junior authority explicitly. Without
     * this, role checks tend to decay into long OR-lists that drift apart.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(Roles.COMPLIANCE_ADMIN).implies(Roles.SENIOR_ANALYST)
                .role(Roles.SENIOR_ANALYST).implies(Roles.ANALYST)
                .build();
    }

    /**
     * Applies the hierarchy to {@code @PreAuthorize} on service methods, so a
     * COMPLIANCE_ADMIN satisfies {@code hasRole('ANALYST')} without every check
     * degenerating into a long OR-list that drifts out of sync.
     */
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Demo identities. Passwords arrive from the environment (see .env.example)
     * and are BCrypt-hashed at startup — no credential is hardcoded or stored
     * in plaintext. A production deployment swaps this bean for an LDAP/OIDC
     * provider without touching any other class.
     */
    @Bean
    public UserDetailsService userDetailsService(SentinelProperties props, PasswordEncoder encoder) {
        var security = props.security();
        return new InMemoryUserDetailsManager(
                User.withUsername("analyst")
                        .password(encoder.encode(security.analystPassword()))
                        .roles(Roles.ANALYST).build(),
                User.withUsername("senior")
                        .password(encoder.encode(security.seniorPassword()))
                        .roles(Roles.SENIOR_ANALYST, Roles.ANALYST).build(),
                User.withUsername("admin")
                        .password(encoder.encode(security.adminPassword()))
                        .roles(Roles.COMPLIANCE_ADMIN, Roles.SENIOR_ANALYST, Roles.ANALYST).build());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           @Qualifier("sentinelCorsSource")
                                           CorsConfigurationSource corsSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsSource))
            // CSRF protection defends browser session cookies. This API is
            // stateless and token/Basic authenticated, so there is no ambient
            // credential for an attacker to ride — CSRF tokens would add
            // ceremony without adding protection.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // Unauthenticated: API documentation and the container healthcheck.
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                     "/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // Ingestion and all reference/rule administration.
                    .requestMatchers("/api/v1/ingestion/**").hasRole(Roles.COMPLIANCE_ADMIN)
                    .requestMatchers("/api/v1/admin/**").hasRole(Roles.COMPLIANCE_ADMIN)
                    // Business rule 8: unmasked customer PII requires seniority.
                    .requestMatchers("/api/v1/customers/*/full").hasRole(Roles.SENIOR_ANALYST)
                    .requestMatchers("/api/v1/**").hasRole(Roles.ANALYST)
                    .anyRequest().authenticated())
            .httpBasic(basic -> {})
            .exceptionHandling(e -> e.accessDeniedHandler((req, res, ex) -> {
                res.setStatus(403);
                res.setContentType("application/problem+json");
                res.getWriter().write("""
                        {"type":"https://sentinel.meridiantrust.com/errors/access-denied",\
                        "title":"Access denied","status":403,\
                        "detail":"Your role does not permit this operation."}""");
            }));

        return http.build();
    }

    @Bean("sentinelCorsSource")
    public CorsConfigurationSource corsConfigurationSource(SentinelProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.security().corsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Trace-Id", "Location"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
