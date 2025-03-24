package de.rptu.cs.exclaim.security;

import de.rptu.cs.exclaim.controllers.ControllerUtils;
import de.rptu.cs.exclaim.data.records.UserRecord;
import de.rptu.cs.exclaim.i18n.CookieLocalesResolver;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.jobs.BackgroundJobExecutor;
import de.rptu.cs.exclaim.jobs.SendSamlAssociationMail;
import de.rptu.cs.exclaim.utils.NameNormalization;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.saml2.Saml2LoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.saml2.Saml2LogoutConfigurer;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.core.OpenSamlInitializationService;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider.ResponseToken;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.metadata.Saml2MetadataResolver;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.RelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.Saml2MetadataFilter;
import org.springframework.security.saml2.provider.service.web.authentication.logout.OpenSaml4LogoutRequestResolver;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.rptu.cs.exclaim.schema.tables.PasswordResets.PASSWORD_RESETS;
import static de.rptu.cs.exclaim.schema.tables.Users.USERS;
import static de.rptu.cs.exclaim.security.SecurityConfig.LOGIN_PAGE;

/**
 * Beans used in {@link SecurityConfig} to configure SAML 2.0 login/logout.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@Slf4j
public class Saml2AuthenticationConfig {
    static {
        OpenSamlInitializationService.initialize();
    }

    // Page that we redirect to after updating userdata
    private static final String SETTINGS_PAGE = "/settings";

    // URL that handles SAML 2.0 logout requests and responses issued by the asserting party
    private static final String LOGOUT_URL = "/logout/saml2/slo";

    // Names of SAML 2.0 attributes we request
    static final String SAMLID_ATTRIBUTE = "urn:oasis:names:tc:SAML:attribute:pairwise-id";
    static final String FIRTNAME_ATTRIBUTE = "urn:oid:2.5.4.42";
    static final String LASTNAME_ATTRIBUTE = "urn:oid:2.5.4.4";
    static final String EMAIL_ATTRIBUTE = "urn:oid:0.9.2342.19200300.100.1.3";
    static final String STUDENT_ID_ATTRIBUTE = "urn:oid:1.3.6.1.4.1.25178.1.2.14";

    // Regular expression for the studentId attribute value
    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("urn:schac:personalUniqueCode:de:.(.*):Matrikelnummer:(\\d+)");

    private final Saml2RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;
    private final ICUMessageSourceAccessor msg;
    private final CookieLocalesResolver cookieLocalesResolver;
    private final SendSamlAssociationMail sendSamlAssociationMail;
    private final BackgroundJobExecutor backgroundJobExecutor;
    private final DSLContext ctx;

    /**
     * Extension of our {@link ExclaimUserPrincipal} class that implements {@link Saml2AuthenticatedPrincipal} such that
     * Spring Boot's default SAML 2.0 logout handling can perform a single logout.
     * <p>
     * We also save the {@code NameIDFormat} that was used by the asserting party because Shibboleth IdP expects our
     * {@code LogoutRequest} to contain the {@code NameIDFormat} attribute with the same value.
     */
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    @Getter(onMethod_ = @Override)
    private static class ExclaimSamlUserPrincipal extends ExclaimUserPrincipal implements Saml2AuthenticatedPrincipal {
        @Getter(AccessLevel.NONE) private final String nameIdFormat;
        private final String name;
        private final String relyingPartyRegistrationId;
        private final List<String> sessionIndexes;

        ExclaimSamlUserPrincipal(UserRecord userRecord, String nameIdFormat, String name, String relyingPartyRegistrationId, List<String> sessionIndexes) {
            super(userRecord);
            this.nameIdFormat = nameIdFormat;
            this.name = name;
            this.relyingPartyRegistrationId = relyingPartyRegistrationId;
            this.sessionIndexes = sessionIndexes;
        }
    }

    /**
     * Configure SAML 2.0 login
     */
    @Bean
    public Customizer<Saml2LoginConfigurer<HttpSecurity>> saml2LoginCustomizer() {
        return saml2Login -> saml2Login
            .relyingPartyRegistrationRepository(relyingPartyRegistrationRepository)
            .authenticationManager(authenticationManager)
            .successHandler(saml2AuthenticationSuccessHandler)
            .loginPage(LOGIN_PAGE);
    }

    /**
     * Configure SAML 2.0 single logout
     */
    @Bean
    public Customizer<Saml2LogoutConfigurer<HttpSecurity>> saml2LogoutCustomizer() {
        OpenSaml4LogoutRequestResolver logoutRequestResolver = new OpenSaml4LogoutRequestResolver(
            new DefaultRelyingPartyRegistrationResolver(relyingPartyRegistrationRepository)
        );
        logoutRequestResolver.setParametersConsumer(parameters -> {
            if (parameters.getAuthentication().getPrincipal() instanceof ExclaimSamlUserPrincipal principal) {
                // Shibboleth IdP wants to have the NameIDFormat to be set
                parameters.getLogoutRequest().getNameID().setFormat(principal.nameIdFormat);
            }
        });
        return saml2Logout -> saml2Logout
            .relyingPartyRegistrationRepository(relyingPartyRegistrationRepository)
            .logoutResponse(logoutResponse -> logoutResponse
                .logoutUrl(LOGOUT_URL)
            )
            .logoutRequest(logoutRequest -> logoutRequest
                .logoutRequestResolver(logoutRequestResolver)
                .logoutUrl(LOGOUT_URL)
            )
            // attempt single logout only if supported by the asserting party
            .addObjectPostProcessor(new ObjectPostProcessor<LogoutFilter>() {
                @Override
                public <O extends LogoutFilter> O postProcess(O logoutFilter) {
                    logoutFilter.setLogoutRequestMatcher(new AndRequestMatcher(
                        new AntPathRequestMatcher("/logout", "POST"),
                        request -> {
                            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                            if (authentication != null && authentication.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal) {
                                RelyingPartyRegistration registration = relyingPartyRegistrationRepository.findByRegistrationId(principal.getRelyingPartyRegistrationId());
                                return registration != null && StringUtils.isNotEmpty(registration.getSingleLogoutServiceLocation());
                            }
                            return false;
                        }
                    ));
                    return logoutFilter;
                }
            });
    }

    /**
     * Remove "X-Frame-Options: DENY" from logout url (required for AP-initiated logout)
     */
    @Bean
    public Customizer<HeadersConfigurer<HttpSecurity>> saml2LogoutHeadersCustomizer(UrlPathHelper urlPathHelper) {
        XFrameOptionsHeaderWriter defaultHeaderWriter = new XFrameOptionsHeaderWriter(XFrameOptionsHeaderWriter.XFrameOptionsMode.DENY);
        return headers -> headers
            .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
            .addHeaderWriter((request, response) -> {
                if (!LOGOUT_URL.equals(urlPathHelper.getPathWithinApplication(request))) {
                    defaultHeaderWriter.writeHeaders(request, response);
                }
            });
    }

    /**
     * A filter that serves our service provider's own SAML 2.0 metadata
     */
    @Bean
    public Saml2MetadataFilter saml2MetadataFilter(Saml2MetadataResolver saml2MetadataResolver) {
        RelyingPartyRegistrationResolver registrationResolver = new DefaultRelyingPartyRegistrationResolver(relyingPartyRegistrationRepository);
        return new Saml2MetadataFilter(registrationResolver, saml2MetadataResolver);
    }


    // Attribute names to store some flags in the request object
    private static final String REQUEST_ATTRIBUTE_NAME_ASSOCIATED_EXISTING_ACCOUNT = Saml2AuthenticationConfig.class.getName() + ".ASSOCIATED_EXISTING_ACCOUNT";
    private static final String REQUEST_ATTRIBUTE_NAME_DEACTIVATED_OLD_LOGIN = Saml2AuthenticationConfig.class.getName() + ".DEACTIVATED_OLD_LOGIN";
    private static final String REQUEST_ATTRIBUTE_NAME_UPDATED_USERDATA = Saml2AuthenticationConfig.class.getName() + ".UPDATED_USERDATA";

    /**
     * Authentication processing logic. Cannot issue redirects, therefore use the request attributes above to pass
     * information to the {@link #saml2AuthenticationSuccessHandler}.
     */
    private final AuthenticationManager authenticationManager = new AuthenticationManager() {
        private final OpenSaml4AuthenticationProvider authenticationProvider = new OpenSaml4AuthenticationProvider();

        /**
         * Extension of {@link Saml2Authentication} that also holds the {@code NameIDFormat} used by the asserting party
         */
        static class Saml2AuthenticationWithNameIdFormat extends Saml2Authentication {
            private final String nameIdFormat;

            private Saml2AuthenticationWithNameIdFormat(Saml2Authentication saml2Authentication, String nameIdFormat) {
                super(
                    (AuthenticatedPrincipal) saml2Authentication.getPrincipal(),
                    saml2Authentication.getSaml2Response(),
                    saml2Authentication.getAuthorities()
                );
                this.nameIdFormat = nameIdFormat;
            }
        }

        {
            // Customize the OpenSaml4AuthenticationProvider to produce Saml2AuthenticationWithNameIdFormat objects
            Converter<ResponseToken, Saml2Authentication> defaultConverter = OpenSaml4AuthenticationProvider.createDefaultResponseAuthenticationConverter();
            authenticationProvider.setResponseAuthenticationConverter(responseToken -> new Saml2AuthenticationWithNameIdFormat(
                Objects.requireNonNull(defaultConverter.convert(responseToken)),
                responseToken.getResponse().getAssertions().get(0).getSubject().getNameID().getFormat()
            ));
        }

        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException {
            authentication = authenticationProvider.authenticate(authentication);
            try {
                Saml2AuthenticatedPrincipal principal = (Saml2AuthenticatedPrincipal) authentication.getPrincipal();
                String nameIdFormat = ((Saml2AuthenticationWithNameIdFormat) authentication).nameIdFormat;
                log.debug(
                    "Successful SAML 2.0 authentication for {} (NameIDFormat: {}) with attributes {}",
                    principal.getName(), nameIdFormat, principal.getAttributes()
                );

                String samlId = getRequiredAttribute(principal, SAMLID_ATTRIBUTE);
                String firstname = getRequiredAttribute(principal, FIRTNAME_ATTRIBUTE);
                String lastname = getRequiredAttribute(principal, LASTNAME_ATTRIBUTE);
                String email = getRequiredAttribute(principal, EMAIL_ATTRIBUTE);
                String studentId = parseStudentIdAttribute(principal.getAttribute(STUDENT_ID_ATTRIBUTE));

                boolean associatedExistingAccount = false;
                boolean deactivatedOldLogin = false;

                // First look for an account that is already associated with this samlId
                log.debug("Locating user with samlId {}", samlId);
                UserRecord userRecord = findUserByCondition(USERS.SAMLID.eq(samlId));

                // If not found, try finding one by studentId
                if (userRecord == null && studentId != null) {
                    log.debug("Locating user with studentId {}", studentId);
                    userRecord = findUserByCondition(USERS.STUDENTID.eq(studentId));

                    if (userRecord != null) {
                        if (userRecord.getSamlId() != null) {
                            log.error(
                                "Cannot authenticate SAML 2.0 user (samlId={}, firstname={}, lastname={}, email={}, studentId={}) because existing account {} has different samlId",
                                samlId, firstname, lastname, email, studentId, userRecord
                            );
                            throw new BadCredentialsException(msg.getMessage("login.saml.cannot-associate-existing-account"));
                        }

                        // Do not allow to associate with an existing account that has admin permissions
                        if (userRecord.getAdmin()) {
                            throw new BadCredentialsException(msg.getMessage("login.saml.no-admin"));
                        }

                        // Associate existing account with current samlId
                        userRecord.setSamlId(samlId);
                        associatedExistingAccount = true;

                        // If the account did not already have the same verified email address, then we deactivate
                        // the old login method such that no other person can access the updated account.
                        if (userRecord.getActivationCode() != null) {
                            deactivatedOldLogin = true;
                            userRecord.setActivationCode(null);
                        } else if (!userRecord.getEmail().equalsIgnoreCase(email)) {
                            deactivatedOldLogin = true;
                            sendSamlAssociationMail.submit(
                                Objects.toString(userRecord.getUsername(), "-"),
                                userRecord.getEmail(),
                                userRecord.getFirstname(),
                                userRecord.getLastname(),
                                userRecord.getLanguage()
                            );
                            backgroundJobExecutor.pollNow();
                        }
                        if (deactivatedOldLogin) {
                            userRecord.setPassword(null);
                            ctx
                                .deleteFrom(PASSWORD_RESETS)
                                .where(PASSWORD_RESETS.USERID.eq(userRecord.getUserId()))
                                .execute();
                        }
                    }
                }

                if (userRecord != null) {
                    // Found existing user
                    log.debug("Found user for authentication in database: {}", userRecord);

                    // Update userdata (using non-short-circuit boolean disjunction!)
                    @SuppressWarnings("ShortCircuitBoolean")
                    boolean updatedUserdata
                        = updateName(firstname, userRecord.getFirstname(), userRecord::setFirstname)
                        | updateName(lastname, userRecord.getLastname(), userRecord::setLastname)
                        | userRecord.setEmailIfChanged(email)
                        | (studentId != null && userRecord.setStudentIdIfChanged(studentId));

                    // Save user record (might also have changes from setting samlId further above!)
                    if (userRecord.changed()) {
                        log.info("Successful SAML 2.0 login, updating userdata {} to {}", userRecord.original(), userRecord);
                        userRecord.update();
                    }

                    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
                        HttpServletRequest request = sra.getRequest();

                        // Save attributes in request object for further processing in saml2AuthenticationSuccessHandler
                        request.setAttribute(REQUEST_ATTRIBUTE_NAME_ASSOCIATED_EXISTING_ACCOUNT, associatedExistingAccount);
                        request.setAttribute(REQUEST_ATTRIBUTE_NAME_DEACTIVATED_OLD_LOGIN, deactivatedOldLogin);
                        request.setAttribute(REQUEST_ATTRIBUTE_NAME_UPDATED_USERDATA, updatedUserdata);

                        // Apply the language that has been stored for that user
                        String language = userRecord.getLanguage();
                        if (language != null) {
                            cookieLocalesResolver.setLocale(request, sra.getResponse(), Locale.forLanguageTag(language));
                        }
                    } else {
                        log.warn("Could not get current request!");
                    }
                } else {
                    // Create new user with data from SAML 2.0 assertion
                    userRecord = ctx.newRecord(USERS);
                    userRecord.setSamlId(samlId);
                    userRecord.setFirstname(firstname);
                    userRecord.setLastname(lastname);
                    userRecord.setStudentId(studentId);
                    userRecord.setEmail(email);
                    userRecord.setLanguage(msg.getBestLanguage());
                    userRecord.insert();
                    log.info("Successful SAML 2.0 login, created new account {}", userRecord);
                }

                return new ExclaimAuthentication(new ExclaimSamlUserPrincipal(
                    userRecord,
                    nameIdFormat,
                    principal.getName(),
                    principal.getRelyingPartyRegistrationId(),
                    principal.getSessionIndexes()
                ));
            } catch (AuthenticationException e) {
                throw e;
            } catch (Exception e) {
                log.error("Exception during SAML 2.0 processing", e);
                throw new BadCredentialsException(msg.getMessage("login.saml.exception"), e);
            }
        }

        private static String getRequiredAttribute(Saml2AuthenticatedPrincipal principal, String attributeName) {
            List<String> values = principal.getAttribute(attributeName);
            if (values == null || values.isEmpty()) {
                throw new IllegalStateException("Successful SAML 2.0 authentication is missing the " + attributeName + " attribute");
            }
            int size = values.size();
            if (size != 1) {
                throw new IllegalStateException("Successful SAML 2.0 authentication has " + size + " occurrences of the " + attributeName + " attribute, but expected a single one");
            }
            String value = values.get(0);
            if (StringUtils.isEmpty(value)) {
                throw new IllegalStateException("Successful SAML 2.0 authentication has a blank " + attributeName + " attribute");
            }
            return value;
        }

        @Nullable
        private static String parseStudentIdAttribute(@Nullable List<String> attributes) {
            String studentId = null;
            if (attributes != null) {
                for (String attribute : attributes) {
                    Matcher matcher = STUDENT_ID_PATTERN.matcher(attribute);
                    if (matcher.matches()) {
                        if (studentId == null) {
                            studentId = matcher.group(2);
                        } else if (!studentId.equals(matcher.group(2))) {
                            throw new IllegalStateException("Successful SAML 2.0 authentication has multiple student ids: " + attributes);
                        }
                    }
                }
            }
            return studentId;
        }

        @Nullable
        private UserRecord findUserByCondition(Condition condition) {
            return ctx
                .selectFrom(USERS)
                .where(condition)
                .forUpdate()
                .fetchOne();
        }

        /**
         * Update the name (firstname/lastname) only if the updated name is not the normalization of the original name.
         * <p>
         * A normalization is a replacement of non-latin characters like German umlauts. Such a replacement is also done
         * by the IdP. We do not want to overwrite a name with its normalized value if it otherwise has not changed.
         *
         * @param updatedName  the new name to set
         * @param originalName the current name
         * @param setter       setter method to set a new name
         * @return whether the name has changed
         */
        private boolean updateName(String updatedName, String originalName, Consumer<String> setter) {
            if (!NameNormalization.normalizeName(originalName).equals(updatedName)) {
                setter.accept(updatedName);
                return true;
            }
            return false;
        }
    };

    /**
     * Issue the redirect after a successful login processed by {@link #authenticationManager}
     */
    private final AuthenticationSuccessHandler saml2AuthenticationSuccessHandler = new SavedRequestAwareAuthenticationSuccessHandler() {
        private final FlashMapManager flashMapManager = new SessionFlashMapManager();

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws ServletException, IOException {
            // Retrieve attributes from request object
            boolean associatedExistingAccount = Boolean.TRUE.equals(request.getAttribute(REQUEST_ATTRIBUTE_NAME_ASSOCIATED_EXISTING_ACCOUNT));
            boolean deactivatedOldLogin = Boolean.TRUE.equals(request.getAttribute(REQUEST_ATTRIBUTE_NAME_DEACTIVATED_OLD_LOGIN));
            boolean updatedUserdata = Boolean.TRUE.equals(request.getAttribute(REQUEST_ATTRIBUTE_NAME_UPDATED_USERDATA));

            if (associatedExistingAccount || deactivatedOldLogin || updatedUserdata) {
                // Messages to show after redirect
                List<String> messages = new ArrayList<>(3);
                if (associatedExistingAccount) {
                    messages.add(msg.getMessage("login.saml.associated-existing-account"));
                }
                if (deactivatedOldLogin) {
                    messages.add(msg.getMessage("login.saml.deactivated-old-login"));
                }
                if (updatedUserdata) {
                    messages.add(msg.getMessage("login.saml.updated-userdata"));
                }

                // Save messages in FlashMap
                FlashMap flashMap = new FlashMap();
                flashMap.put(ControllerUtils.MessageType.SUCCESS.messageKey(), messages);
                flashMap.setTargetRequestPath(SETTINGS_PAGE);
                flashMapManager.saveOutputFlashMap(flashMap, request, response);

                // Issue redirect to settings page
                getRedirectStrategy().sendRedirect(request, response, SETTINGS_PAGE);
            } else {
                // Apply default action (redirect to saved request, if any, otherwise home)
                super.onAuthenticationSuccess(request, response, authentication);
            }
        }
    };
}
