package de.rptu.cs.exclaim.security;

import de.rptu.cs.exclaim.data.records.UserRecord;
import de.rptu.cs.exclaim.i18n.CookieLocalesResolver;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.schema.tables.Assistants;
import de.rptu.cs.exclaim.schema.tables.Tutors;
import de.rptu.cs.exclaim.schema.tables.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;

import static de.rptu.cs.exclaim.schema.tables.Assistants.ASSISTANTS;
import static de.rptu.cs.exclaim.schema.tables.Tutors.TUTORS;
import static de.rptu.cs.exclaim.schema.tables.Users.USERS;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExclaimAuthenticationProvider implements AuthenticationProvider {
    private final ExclaimPasswordEncoder pe;
    private final ICUMessageSourceAccessor msg;
    private final CookieLocalesResolver cookieLocalesResolver;
    private final DSLContext ctx;
    private final AccessChecker accessChecker;

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }

    @Override
    @Transactional
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();
        log.debug("Attempting authentication for username {}", username);
        Users u = USERS.as("u");
        Tutors t = TUTORS.as("t");
        Assistants a = ASSISTANTS.as("a");
        UserWithPermissions userWithPermissions = ctx
            .select(
                u,
                DSL.field(DSL.exists(DSL.selectOne().from(a).where(a.USERID.eq(u.USERID)))).as("assistantForAnyExercise"),
                DSL.field(DSL.exists(DSL.selectOne().from(t).where(t.USERID.eq(u.USERID)))).as("tutorForAnyExercise")
            )
            .from(u)
            .where(u.USERNAME.eq(username))
            .forUpdate()
            .fetchOne(Records.mapping(UserWithPermissions::new));

        if (userWithPermissions != null) {
            UserRecord userRecord = userWithPermissions.getUser();
            log.debug("Found user for authentication in database: {}", userWithPermissions);
            if (userRecord.getActivationCode() != null) {
                log.debug("Failing authentication due to not verified account");
                throw new DisabledException(msg.getMessage("login.account-is-disabled"));
            }
            String encodedPassword = userRecord.getPassword();
            if (StringUtils.isEmpty(encodedPassword)) {
                log.debug("User has no password, needs to authenticate via SAML");
                throw new BadCredentialsException(msg.getMessage("login.require-saml"));
            }
            if (pe.matches(password, encodedPassword)) {
                log.debug("Authentication successful");
                if (pe.upgradeEncoding(encodedPassword)) {
                    // Password hash uses outdated mechanism, generate a new one
                    log.debug("Updating encoded password");
                    userRecord.setPassword(pe.encode(password));
                    userRecord.update();
                    log.debug("Updated encoded password for {}", userRecord);
                }

                // Apply the language that has been stored for that user
                String language = userRecord.getLanguage();
                if (language != null) {
                    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
                        cookieLocalesResolver.setLocale(sra.getRequest(), sra.getResponse(), Locale.forLanguageTag(language));
                    } else {
                        log.warn("Could not get current request!");
                    }
                }

                // Update the currently authenticated user
                accessChecker.updateCachedUser(userWithPermissions);
                return new ExclaimAuthentication(new ExclaimUserPrincipal(userRecord));
            } else {
                log.debug("Failing authentication due to invalid password");
            }
        } else {
            log.debug("Failing authentication due to invalid username");
        }
        throw new BadCredentialsException(msg.getMessage("login.invalid"));
    }
}
