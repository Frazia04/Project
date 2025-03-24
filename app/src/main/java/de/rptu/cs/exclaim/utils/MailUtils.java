package de.rptu.cs.exclaim.utils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;

import java.io.UnsupportedEncodingException;

@Slf4j
public class MailUtils {
    /**
     * Check whether the given MailException denotes a temporary failure.
     * <p>
     * The check assumes that the MailException got thrown when sending a single message to a single recipient.
     * Permanent failures are any failures that are not a MailSendException, as well as permanently rejected recipient
     * addresses (error codes 500-599). All remaining MailSendExceptions are treated as temporary failures.
     *
     * @param exception the exception to check
     * @return whether it is a temporary failure
     */
    public static boolean isTemporaryFailure(MailException exception) {
        // Only exceptions for sending can be temporary
        if (exception instanceof MailSendException mailSendException) {
            // A single MailSendException can contain multiple MessagingException
            for (Exception messageException : mailSendException.getMessageExceptions()) {
                // The top level MessagingException can start a linked list that we need to follow
                while (messageException instanceof MessagingException messagingException) {
                    // We look for a rejected recipient address
                    if (messageException instanceof SMTPAddressFailedException smtpAddressFailedException) {
                        int rc = smtpAddressFailedException.getReturnCode();
                        if (rc >= 500 && rc < 600) {
                            return false; // permanent error code
                        }
                    }
                    messageException = messagingException.getNextException();
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Encode the given display name and address to the RFC5322 "name-addr" form.
     *
     * @param name    the display name
     * @param address the address
     * @return the encoded address with display name
     */
    public static String encodeNameAndAddress(String name, String address) {
        try {
            return new InternetAddress(address, name, "utf-8").toString();
        } catch (UnsupportedEncodingException e) {
            log.error("Could not encode name {} with address {}", name, address, e);
            return address;
        }
    }
}
