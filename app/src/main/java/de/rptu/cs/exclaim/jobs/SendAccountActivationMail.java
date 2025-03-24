package de.rptu.cs.exclaim.jobs;

import de.rptu.cs.exclaim.ExclaimProperties;
import de.rptu.cs.exclaim.controllers.RegistrationController;
import de.rptu.cs.exclaim.data.records.UserRecord;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.schema.enums.BackgroundJobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Nullable;
import java.util.Map;

import static de.rptu.cs.exclaim.jobs.PayloadHelpers.longToPayload;
import static de.rptu.cs.exclaim.jobs.PayloadHelpers.payloadToInt;
import static de.rptu.cs.exclaim.schema.tables.Users.USERS;
import static de.rptu.cs.exclaim.utils.MailUtils.encodeNameAndAddress;
import static de.rptu.cs.exclaim.utils.MailUtils.isTemporaryFailure;

@Service
@Slf4j
@RequiredArgsConstructor
public class SendAccountActivationMail implements JobService {
    private static final BackgroundJobType TYPE = BackgroundJobType.SEND_ACCOUNT_ACTIVATION_MAIL;
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
        UserRecord userRecord = ctx.fetchOne(USERS, USERS.USERID.eq(userId));
        if (userRecord != null) {
            String activationCode = userRecord.getActivationCode();
            if (activationCode != null) {
                String username = userRecord.getUsername();
                if (username != null) {
                    log.info("Sending activation mail to user {}", userRecord);
                    SimpleMailMessage mail = new SimpleMailMessage();
                    mail.setFrom(encodeNameAndAddress("ExClaim", exclaimProperties.getEmailSender()));
                    mail.setTo(encodeNameAndAddress(userRecord.getFirstname() + " " + userRecord.getLastname(), userRecord.getEmail()));
                    mail.setSubject(msg.getMessage("register.mail.subject", userRecord));
                    mail.setText(msg.getMessage("register.mail.body", Map.of(
                        "firstname", userRecord.getFirstname(),
                        "lastname", userRecord.getLastname(),
                        "username", username,
                        "activationURL", UriComponentsBuilder
                            .fromUriString(exclaimProperties.getPublicUrl())
                            .path(RegistrationController.ACTIVATE_PATH)
                            .queryParam("user", username)
                            .queryParam("code", activationCode)
                            .build().toString(),
                        "publicURL", exclaimProperties.getPublicUrl(),
                        "adminContact", exclaimProperties.getAdminContact()
                    ), userRecord));
                    try {
                        javaMailSender.send(mail);
                    } catch (MailSendException e) {
                        if (isTemporaryFailure(e)) {
                            throw new JobFailedTemporarilyException(String.format("Exception while sending activation mail to user %s", userRecord), e);
                        } else {
                            throw new JobFailedPermanentlyException(String.format("Exception while sending activation mail to user %s", userRecord), e);
                        }
                    }
                } else {
                    log.error("Cannot send an activation mail to user {} because the user has no username.", userRecord);
                }
            } else {
                log.info("No need to send an activation mail to user {} because the user has already been activated.", userRecord);
            }
        } else {
            log.info("No need to send an activation mail to user id {} because the user no longer exists.", userId);
        }
    }

    public void submit(int userId) {
        backgroundJobExecutor.submit(TYPE, longToPayload(userId));
    }
}
