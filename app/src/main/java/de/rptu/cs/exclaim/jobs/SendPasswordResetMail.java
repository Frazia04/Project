package de.rptu.cs.exclaim.jobs;

import de.rptu.cs.exclaim.ExclaimProperties;
import de.rptu.cs.exclaim.controllers.PasswordResetController;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor.UserLocale;
import de.rptu.cs.exclaim.schema.enums.BackgroundJobType;
import de.rptu.cs.exclaim.schema.tables.PasswordResets;
import de.rptu.cs.exclaim.schema.tables.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static de.rptu.cs.exclaim.jobs.PayloadHelpers.longToPayload;
import static de.rptu.cs.exclaim.jobs.PayloadHelpers.payloadToInt;
import static de.rptu.cs.exclaim.schema.tables.PasswordResets.PASSWORD_RESETS;
import static de.rptu.cs.exclaim.utils.MailUtils.encodeNameAndAddress;
import static de.rptu.cs.exclaim.utils.MailUtils.isTemporaryFailure;

@Service
@Slf4j
@RequiredArgsConstructor
public class SendPasswordResetMail implements JobService {
    private static final BackgroundJobType TYPE = BackgroundJobType.SEND_PASSWORD_RESET_MAIL;
    private final BackgroundJobExecutor backgroundJobExecutor;
    private final DSLContext ctx;
    private final JavaMailSender javaMailSender;
    private final ExclaimProperties exclaimProperties;
    private final ICUMessageSourceAccessor msg;

    @Override
    public BackgroundJobType getType() {
        return TYPE;
    }

    @Override
    public void execute(@Nullable byte[] payload, JobContext context) {
        int userId = payloadToInt(payload);
        log.debug("Executing job for userId {}", userId);

        PasswordResets p = PASSWORD_RESETS.as("p");
        Users u = p.user().as("u");
        var result = ctx
            .select(
                u.USERNAME,
                u.FIRSTNAME,
                u.LASTNAME,
                u.EMAIL,
                u.LANGUAGE,
                p.CODE,
                p.VALID_UNTIL
            )
            .from(p)
            .where(
                u.USERID.eq(userId),
                u.USERNAME.isNotNull(),
                p.VALID_UNTIL.ge(LocalDateTime.now(ZoneOffset.UTC))
            )
            .fetchOne();
        if (result != null) {
            String username = result.value1();
            String firstname = result.value2();
            String lastname = result.value3();
            String email = result.value4();
            UserLocale userLocale = new UserLocale(result.value5());
            String code = result.value6();
            LocalDateTime validUntil = result.value7();
            log.info("Sending password reset mail to user id {}", userId);
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(encodeNameAndAddress("ExClaim", exclaimProperties.getEmailSender()));
            mail.setTo(encodeNameAndAddress(firstname + " " + lastname, email));
            mail.setSubject(msg.getMessage("request-password.mail.subject", userLocale));
            mail.setText(msg.getMessage("request-password.mail.body", Map.of(
                "firstname", firstname,
                "lastname", lastname,
                "username", username,
                "resetURL", UriComponentsBuilder
                    .fromUriString(exclaimProperties.getPublicUrl())
                    .path(PasswordResetController.RESET_PATH)
                    .queryParam("user", username)
                    .queryParam("reset", code)
                    .build().toString(),
                "expirationTime", validUntil.toInstant(ZoneOffset.UTC).toEpochMilli(),
                "publicURL", exclaimProperties.getPublicUrl(),
                "adminContact", exclaimProperties.getAdminContact()
            ), userLocale));
            try {
                javaMailSender.send(mail);
            } catch (MailSendException e) {
                if (isTemporaryFailure(e)) {
                    throw new JobFailedTemporarilyException(String.format("Exception while sending password reset mail to user id %s", userId), e);
                } else {
                    throw new JobFailedPermanentlyException(String.format("Exception while sending password reset mail to user id %s", userId), e);
                }
            }
        } else {
            log.info("No need to send a password reset mail to user id {} because there is no valid password reset record.", userId);
        }
    }

    public void submit(int userId) {
        backgroundJobExecutor.submit(TYPE, longToPayload(userId));
    }
}
