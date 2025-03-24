package de.rptu.cs.exclaim.security;

import org.jasypt.util.password.rfc2307.RFC2307SMD5PasswordEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * ExClaim uses BCrypt password encoding.
 * Existing users might still have hashes using the legacy MD5 method; this password encoder recommends to upgrade those hashes.
 */
@Component
public class ExclaimPasswordEncoder implements PasswordEncoder {
    private final String MD5_PREFIX = "{SMD5}";
    private final RFC2307SMD5PasswordEncryptor peMD5 = new RFC2307SMD5PasswordEncryptor();
    private final PasswordEncoder peBCrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(@NonNull CharSequence rawPassword) {
        return peBCrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(@NonNull CharSequence rawPassword, @NonNull String encodedPassword) {
        return encodedPassword.startsWith(MD5_PREFIX)
            ? peMD5.checkPassword(rawPassword.toString(), encodedPassword)
            : peBCrypt.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(@NonNull String encodedPassword) {
        return encodedPassword.startsWith(MD5_PREFIX) || peBCrypt.upgradeEncoding(encodedPassword);
    }
}
