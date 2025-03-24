package de.rptu.cs.exclaim.jobs;

import com.fasterxml.jackson.databind.ObjectReader;
import de.rptu.cs.exclaim.ExclaimProperties;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor.UserLocale;
import de.rptu.cs.exclaim.schema.enums.BackgroundJobType;
import de.rptu.cs.exclaim.utils.JsonUtils;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;

import static de.rptu.cs.exclaim.jobs.PayloadHelpers.payloadToString;
import static de.rptu.cs.exclaim.jobs.PayloadHelpers.stringToPayload;
import static de.rptu.cs.exclaim.utils.MailUtils.encodeNameAndAddress;
import static de.rptu.cs.exclaim.utils.MailUtils.isTemporaryFailure;

@Service
@Slf4j
@RequiredArgsConstructor
public class SendSamlAssociationMail implements JobService {
    private static final BackgroundJobType TYPE = BackgroundJobType.SEND_SAML_ASSOCIATION_MAIL;
    private final BackgroundJobExecutor backgroundJobExecutor;
    private final JavaMailSender javaMailSender;
    private final ExclaimProperties exclaimProperties;
    private final ICUMessageSourceAccessor msg;
    private final ObjectReader objectReader;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @SuppressWarnings("NullAway")
    private static class Payload {
        String username;
        String email;
        String firstname;
        String lastname;
        @Nullable String language;
    }

    @Override
    public BackgroundJobType getType() {
        return TYPE;
    }

    @Override
    public void execute(@Nullable byte[] payload, JobContext context) throws IOException {
        Payload data = objectReader.readValue(payloadToString(payload), Payload.class);
        log.info("Sending information mail about SAML account association for {}", data);
        UserLocale userLocale = new UserLocale(data.language);
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(encodeNameAndAddress("ExClaim", exclaimProperties.getEmailSender()));
        mail.setTo(encodeNameAndAddress(data.firstname + " " + data.lastname, data.email));
        mail.setSubject(msg.getMessage("login.saml.associated-existing-account-mail.subject", userLocale));
        mail.setText(msg.getMessage("login.saml.associated-existing-account-mail.body", Map.of(
            "firstname", data.firstname,
            "lastname", data.lastname,
            "username", data.username,
            "publicURL", exclaimProperties.getPublicUrl(),
            "adminContact", exclaimProperties.getAdminContact()
        ), userLocale));
        try {
            javaMailSender.send(mail);
        } catch (MailSendException e) {
            if (isTemporaryFailure(e)) {
                throw new JobFailedTemporarilyException(String.format("Exception while sending SAML association mail to %s", data), e);
            } else {
                throw new JobFailedPermanentlyException(String.format("Exception while sending SAML association mail to %s", data), e);
            }
        }
    }

    public void submit(String username, String email, String firstname, String lastname, @Nullable String language) {
        backgroundJobExecutor.submit(TYPE, stringToPayload(JsonUtils.toJson(
            new Payload(username, email, firstname, lastname, language)
        )));
    }
}
