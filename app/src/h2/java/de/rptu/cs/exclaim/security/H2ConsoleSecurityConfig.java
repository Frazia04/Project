package de.rptu.cs.exclaim.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.h2.server.web.JakartaWebServlet;
import org.h2.server.web.WebServer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.h2.H2ConsoleProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import java.lang.reflect.Field;
import java.net.URI;
import java.sql.DriverManager;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * Configuration for the H2 web console interface
 * <p>
 * The H2 web console is enabled by spring-boot-devtools in development only. As additional precaution, we only allow
 * access from localhost. The console provides a web gui to inspect and interact with the database.
 * <p>
 * Instead of providing the user of the H2 web console with a login page, we automatically start a session using the
 * configured H2 database. This way, the user does not need to know and enter the JDBC URL.
 *
 * @see org.springframework.boot.autoconfigure.h2.H2ConsoleAutoConfiguration
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.h2.console", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class H2ConsoleSecurityConfig {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain h2ConsoleSecurityFilterChain(
        HttpSecurity http,
        H2ConsoleProperties h2ConsoleProperties,
        ServletRegistrationBean<JakartaWebServlet> h2Console,
        DataSourceProperties dataSourceProperties
    ) throws Exception {
        String prefix = h2ConsoleProperties.getPath().replaceFirst("/$", "");
        return http
            .securityMatcher(antMatcher(prefix + "/**"))

            // disable Spring Boot sessions (H2 web console has its own session management)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // allow requests from localhost only
            .authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
                .anyRequest().access(new WebExpressionAuthorizationManager("hasIpAddress('127.0.0.0/8') or hasIpAddress('::1')"))
            )

            // web interface uses frames -> need to allow
            .headers(headers -> headers.frameOptions(FrameOptionsConfig::sameOrigin))

            // ignore csrf check, but still provide the token on error pages, i.e. not just .disable()
            .csrf(csrf -> csrf.requireCsrfProtectionMatcher(r -> false))

            // automatically log into H2 web console, such that the user does not need to know the jdbc url
            .addFilterAfter((request, response, chain) -> {
                if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
                    if (httpRequest.getRequestURI().endsWith("/login.jsp")) {
                        try {
                            // We need the org.h2.server.web.WebServer instance, which is in a private field of
                            // org.h2.server.web.WebServlet. We can extract it using reflection.
                            Field h2ServerField = JakartaWebServlet.class.getDeclaredField("server");
                            h2ServerField.setAccessible(true);
                            WebServer h2Server = (WebServer) h2ServerField.get(h2Console.getServlet());

                            // Create a new H2 web console session using a fresh database connection. Do not use a
                            // connection from Spring's connection pool, because our connection will be managed by
                            // the H2 web console, potentially violating the pool's assumptions.
                            URI uri = new URI(h2Server.addSession(DriverManager.getConnection(
                                dataSourceProperties.getUrl(),
                                dataSourceProperties.getUsername(),
                                dataSourceProperties.getPassword()
                            )));

                            // We need to adjust the returned uri (replace host, add prefix)
                            String adjustedUri = prefix + uri.getPath() + "?" + uri.getQuery();
                            log.debug("Auto login to H2 web console successful, redirecting to {}", adjustedUri);
                            httpResponse.sendRedirect(httpRequest.getContextPath() + adjustedUri);
                            return; // skip remaining filter chain
                        } catch (Exception e) {
                            log.error("Could not auto login to H2 web console", e);
                        }
                    }
                }
                chain.doFilter(request, response);
            }, AuthorizationFilter.class)

            .build();
    }
}
