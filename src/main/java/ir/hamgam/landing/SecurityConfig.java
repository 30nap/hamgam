package ir.hamgam.landing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
class SecurityConfig {

    /**
     * The browser itself refuses anything not served from this origin, which enforces the
     * "zero external requests" rule even if a template is edited carelessly later.
     */
    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; style-src 'unsafe-inline'; "
            + "font-src 'self'; script-src 'self'; img-src 'self' data:; connect-src 'self'; "
            + "form-action 'self'; base-uri 'none'; frame-ancestors 'none'";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokens = new CookieCsrfTokenRepository();
        csrfTokens.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin", "/admin/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(basic -> basic.realmName("Hamgam admin"))
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Stateless double-submit cookie. The form-start beacon carries no state worth protecting.
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens).ignoringRequestMatchers("/e/**"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)))
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, denied) -> {
                    if (denied instanceof CsrfException) {
                        // Stale tab or cookies cleared: send the visitor back to a fresh form.
                        response.sendRedirect("/#form");
                    } else {
                        response.sendError(403);
                    }
                }));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService adminUsers(AppProperties properties, PasswordEncoder encoder) {
        AppProperties.Admin admin = properties.admin();
        if (admin == null || !StringUtils.hasText(admin.username()) || !StringUtils.hasText(admin.password())) {
            throw new IllegalStateException(
                    "ADMIN_USERNAME and ADMIN_PASSWORD must be set (the admin page would be unprotected otherwise)");
        }
        return new InMemoryUserDetailsManager(User.withUsername(admin.username())
                .password(encoder.encode(admin.password()))
                .roles("ADMIN")
                .build());
    }
}
