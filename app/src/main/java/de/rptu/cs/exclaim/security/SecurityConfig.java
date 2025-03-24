package de.rptu.cs.exclaim.security;

import com.fasterxml.jackson.databind.ObjectWriter;
import de.rptu.cs.exclaim.ExclaimProperties;
import de.rptu.cs.exclaim.api.FECsrfCheckFailed;
import de.rptu.cs.exclaim.frontend.FrontendAuthenticationFailureHandler;
import de.rptu.cs.exclaim.frontend.FrontendAuthenticationSuccessHandler;
import de.rptu.cs.exclaim.frontend.FrontendRoutesFilter;
import de.rptu.cs.exclaim.security.AutomaticDatabaseTransaction.CompleteAutomaticTransactionFilter;
import de.rptu.cs.exclaim.security.AutomaticDatabaseTransaction.CompleteAutomaticTransactionHandlerInterceptor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.saml2.Saml2LoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.saml2.Saml2LogoutConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.saml2.provider.service.web.Saml2MetadataFilter;
import org.springframework.security.saml2.provider.service.web.authentication.Saml2WebSsoAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.session.ForceEagerSessionCreationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static de.rptu.cs.exclaim.frontend.api.ConfigurationController.CONFIGURATION_PATH;
import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;
import static org.springframework.security.web.util.matcher.RegexRequestMatcher.regexMatcher;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig implements WebMvcConfigurer {
    public static final String LOGIN_PAGE = "/login";
    public static final String LOGIN_ENDPOINT = "/api/login";
    public static final String ERROR_PAGE = "/error";

    private final CompleteAutomaticTransactionHandlerInterceptor completeAutomaticTransactionHandlerInterceptor;
    private final ObjectWriter objectWriter;

    /**
     * Configuration for static files
     */
    @Bean
    @Order(1)
    public SecurityFilterChain staticFilesSecurityFilterChain(
        HttpSecurity http,
        UrlPathHelper urlPathHelper,
        ExclaimProperties exclaimProperties
    ) throws Exception {
        http
            .securityMatcher(regexMatcher("/(?:favicon\\.(?:ico|svg)|logo\\.png|webjars|css|js|fonts|assets|docs|proof-tree-generator|recursion-tutor)(?:/.*)?|" + CONFIGURATION_PATH))

            // disable Spring Boot sessions
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // disable headers that prevent caching
            .headers(headers -> headers.cacheControl(HeadersConfigurer.CacheControlConfig::disable))

            // serve index.html if accessing directory path
            .addFilterAfter(new OncePerRequestFilter() {
                private final Pattern indexPattern = Pattern.compile("/(?:docs|proof-tree-generator|recursion-tutor)(?:/(?:.+/)*[^.]*)?");
                private final ClassLoader classLoader = SecurityConfig.class.getClassLoader();

                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
                    String path;
                    if ("GET".equals(request.getMethod())
                        && indexPattern.matcher(path = urlPathHelper.getPathWithinApplication(request)).matches()
                    ) {
                        if (path.endsWith("/")) {
                            // Path is a directory with trailing slash. Serve index.html, if it exists.
                            if (classLoader.getResource("static" + path + "index.html") != null) {
                                request
                                    .getRequestDispatcher(path + "index.html")
                                    .forward(request, response);
                                return;
                            }
                        } else if (classLoader.getResource("static" + path + "/index.html") != null) {
                            // Path is a directory containing an index.html file, but is missing the trailing slash.
                            // -> Send an HTTP redirect to add the missing trailing slash.
                            response.sendRedirect(path + "/");
                            return;
                        }
                    }

                    filterChain.doFilter(request, response);
                }
            }, AuthorizationFilter.class);

        // Setup protection for docs
        String docsPassword = exclaimProperties.getDocsPassword();
        if (StringUtils.isNotEmpty(docsPassword)) {
            http
                .authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
                    .requestMatchers(regexMatcher("/docs(?:$|/.*)")).authenticated()
                    .anyRequest().permitAll()
                )
                .httpBasic(httpBasic -> httpBasic.realmName("Documentation"))
                .userDetailsService(new InMemoryUserDetailsManager(User.builder()
                    .username("docs")
                    .password(docsPassword)
                    .build()
                ));
        }

        return http.build();
    }

    /**
     * Configuration for the main web application
     */
    @Bean
    @Order(2)
    public SecurityFilterChain exclaimSecurityFilterChain(
        HttpSecurity http,
        Customizer<Saml2LoginConfigurer<HttpSecurity>> saml2LoginCustomizer,
        Customizer<Saml2LogoutConfigurer<HttpSecurity>> saml2LogoutCustomizer,
        Customizer<HeadersConfigurer<HttpSecurity>> saml2LogoutHeadersCustomizer,
        Saml2MetadataFilter saml2MetadataFilter,
        FrontendRoutesFilter frontendRoutesFilter,
        FrontendAuthenticationFailureHandler frontendAuthenticationFailureHandler,
        FrontendAuthenticationSuccessHandler frontendAuthenticationSuccessHandler,
        CompleteAutomaticTransactionFilter completeAutomaticTransactionFilter
    ) throws Exception {
        return http
            .securityMatcher(regexMatcher("/(?:api|login|logout|saml2)(?:/.*)?|/index.html"))

            // Always create sessions such that we have a csrf token
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.ALWAYS))

            // Enable login/logout with username and password
            .formLogin(formLogin -> formLogin
                .loginPage(LOGIN_PAGE)
                .loginProcessingUrl(LOGIN_ENDPOINT)
                .failureHandler(frontendAuthenticationFailureHandler)
                .successHandler(frontendAuthenticationSuccessHandler)
            )
            .logout(withDefaults())

            // Enable SAML 2.0 login/logout
            .saml2Login(saml2LoginCustomizer)
            .saml2Logout(saml2LogoutCustomizer)
            .headers(saml2LogoutHeadersCustomizer)
            .addFilterBefore(saml2MetadataFilter, Saml2WebSsoAuthenticationFilter.class)

            // Forward requests matching frontend routes to index.html
            .addFilterBefore(frontendRoutesFilter, ForceEagerSessionCreationFilter.class)

            // Complete automatically started database transaction
            .addFilterBefore(completeAutomaticTransactionFilter, AuthorizationFilter.class)

            // Use HTTP status codes for authentication/authorization errors
            .exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.addHeader("WWW-Authenticate", "Form");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    if (accessDeniedException instanceof CsrfException) {
                        // Provide correct csrf token if csrf check fails
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        objectWriter.writeValue(response.getOutputStream(), new FECsrfCheckFailed(
                            request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrfToken
                                ? csrfToken.getToken()
                                : null
                        ));
                    }
                })
            )

            .build();
    }

    /**
     * Configuration for the main web application (for thymeleaf-based controllers)
     */
    @Bean
    @Order(3)
    public SecurityFilterChain exclaimLegacySecurityFilterChain(
        HttpSecurity http,
        ExclaimProperties exclaimProperties,
        ObjectProvider<PublicPath> publicPathsProvider,
        FrontendRoutesFilter frontendRoutesFilter,
        CompleteAutomaticTransactionFilter completeAutomaticTransactionFilter,
        AccessChecker accessChecker
    ) throws Exception {
        // We collect the public paths via an ObjectProvider instead of hardcoding them here. This way, we can
        // define public paths directly in controllers where they are handled. Furthermore, we can also define
        // additional public paths during testing.
        List<String> publicPaths = Stream.concat(
            Stream.of(LOGIN_PAGE, ERROR_PAGE),
            publicPathsProvider.stream().flatMap(p -> p.getAntPatterns().stream())
        ).toList();
        log.info("Public paths: {}", publicPaths);

        // Authentication entry points
        AuthenticationEntryPoint http403Forbidden = new Http403ForbiddenEntryPoint();
        AuthenticationEntryPoint redirectToLogin = new LoginUrlAuthenticationEntryPoint(LOGIN_PAGE);

        return http
            // Always create sessions, otherwise there is no csrf token for the error template
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.ALWAYS))

            // Authentication requirements
            .authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
                // Metrics are limited by the exclaim.metrics.access property, defaults to localhost ip address
                .requestMatchers(antMatcher("/metrics")).access(new WebExpressionAuthorizationManager(exclaimProperties.getMetrics().getAccess()))

                // Allow public paths without authentication
                .requestMatchers(publicPaths.stream().map(AntPathRequestMatcher::antMatcher).toArray(AntPathRequestMatcher[]::new)).permitAll()

                // All other paths are authenticated
                .anyRequest().authenticated()
            )

            // Forward requests matching frontend routes to index.html
            .addFilterBefore(frontendRoutesFilter, ForceEagerSessionCreationFilter.class)

            // Complete automatically started database transaction
            .addFilterBefore(completeAutomaticTransactionFilter, AuthorizationFilter.class)

            // Load the user for every request, thereby triggering automatic creation of a database transaction.
            // This filter needs to be placed between CompleteAutomaticTransactionFilter and AuthorizationFilter such
            // that AuthorizationFilter observes when a user has been deleted from database.
            // This filter is required by the legacy application because Thymeleaf templates always contain user data.
            .addFilterAfter(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
                    accessChecker.getUserWithPermissionsOpt();
                    filterChain.doFilter(request, response);
                }
            }, CompleteAutomaticTransactionFilter.class)

            // For unauthenticated requests we apply two different strategies:
            .exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint((request, response, authException) -> {
                    if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                        // Ajax requests get a 403 error code
                        http403Forbidden.commence(request, response, authException);
                    } else {
                        // Other requests are redirected to the login page
                        redirectToLogin.commence(request, response, authException);
                    }
                })
            )

            .build();
    }

    /**
     * Paths that are not protected via authentication (for login, registration, password reset, ...).
     */
    @Value
    public static class PublicPath {
        List<String> antPatterns;

        public PublicPath(String... antPatterns) {
            this.antPatterns = List.of(antPatterns);
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(completeAutomaticTransactionHandlerInterceptor);
    }
}
