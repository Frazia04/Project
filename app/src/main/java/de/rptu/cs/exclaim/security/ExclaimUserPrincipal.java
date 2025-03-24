package de.rptu.cs.exclaim.security;

import de.rptu.cs.exclaim.data.interfaces.IUser;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.lang.Nullable;
import org.springframework.security.core.AuthenticatedPrincipal;

import java.io.Serial;
import java.io.Serializable;


/**
 * A container storing the user id of an authenticated user, used by {@link ExclaimAuthentication}.
 * <p>
 * This class must be {@link Serializable} because the principal gets serialized between application restarts (as part
 * of the session data). Since jOOQ records are nor serializable, we only store the user id. {@link AccessChecker} is in
 * charge of loading the corresponding user data on every http request (we want current data for each request anyway).
 * <p>
 * We need the user's last known password hash such that {@link AccessChecker} can invalidate a session if the user's
 * password has changed since the user logged in.
 */
@EqualsAndHashCode
@ToString
public class ExclaimUserPrincipal implements Serializable, AuthenticatedPrincipal {
    @Serial private static final long serialVersionUID = 1L;

    final int userId;
    @Nullable String password;

    public ExclaimUserPrincipal(IUser user) {
        this.userId = user.getUserId();
        this.password = user.getPassword();
    }

    @Override
    public String getName() {
        return "User ID " + userId;
    }
}
