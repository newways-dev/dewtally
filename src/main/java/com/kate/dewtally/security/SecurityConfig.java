package com.kate.dewtally.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import static com.kate.dewtally.security.Roles.*;

/**
 * Every API request needs a valid token from Keycloak, and the user's role decides what they may do.
 * The whole permission matrix is here, in one place. Anything under /api not listed is denied.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    private static JwtAuthenticationConverter authenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(Roles::authorities);
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        // 401: keep the standard WWW-Authenticate header, add a problem+json body
        AuthenticationEntryPoint notLoggedIn = (request, response, exception) -> {
            bearer.commence(request, response, exception);
            ProblemResponses.write(response, 401, "Unauthorized", "A valid token is required");
        };
        AccessDeniedHandler notAllowed = (request, response, exception) ->
                ProblemResponses.write(response, 403, "Forbidden", "Your role does not allow this action");

        http
                .authorizeHttpRequests(auth -> auth
                        // Health checks and metrics; on the api they are on a port not published outside Docker
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        // Reading: every business role
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole(CLERK, APPROVER, ADMIN)
                        // Entering invoices: clerk
                        .requestMatchers(HttpMethod.POST, "/api/documents").hasRole(CLERK)
                        .requestMatchers(HttpMethod.PUT, "/api/invoices/*/data").hasRole(CLERK)
                        // Deciding on invoices: approver
                        .requestMatchers(HttpMethod.POST, "/api/invoices/*/accept", "/api/invoices/*/reject")
                        .hasRole(APPROVER)
                        // Verifying an IBAN: a second person, approver or admin (the four-eyes check is in the domain)
                        .requestMatchers(HttpMethod.POST, "/api/suppliers/*/ibans/*/verify").hasAnyRole(APPROVER, ADMIN)
                        // Supplier master data: admin
                        .requestMatchers(HttpMethod.POST, "/api/suppliers", "/api/suppliers/*/ibans",
                                "/api/suppliers/*/block", "/api/suppliers/*/unblock").hasRole(ADMIN)
                        // Deny by default: a new endpoint without a rule is closed, not accidentally open
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter()))
                        .authenticationEntryPoint(notLoggedIn)
                        .accessDeniedHandler(notAllowed))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(notLoggedIn)
                        .accessDeniedHandler(notAllowed))
                // After the token and the role are checked: the rest of the request runs as the token's tenant
                .addFilterAfter(new TenantFilter(), AuthorizationFilter.class)
                // A token comes with every request: no server-side session, no login cookie
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF attacks abuse cookies the browser sends by itself; a bearer token is never sent automatically
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}