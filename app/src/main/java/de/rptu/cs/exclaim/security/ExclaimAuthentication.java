package de.rptu.cs.exclaim.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

/**
 * An {@link Authentication} representing an authenticated user.
 * <p>
 * The <code>principal</code> is an {@link ExclaimUserPrincipal}.
 * There are no <code>credentials</code> or {@link GrantedAuthority}s stored in this authentication.
 * The current authorities (permissions) are retrieved directly from the database when needed.
 * <p>
 * This class is {@link Serializable} (inherited from {@link Authentication}) since sessions will be persisted between
 * application restarts.
 */
@RequiredArgsConstructor
public class ExclaimAuthentication implements Authentication {
    @Serial private static final long serialVersionUID = 1L;

    @Getter(onMethod_ = @Override)
    private final ExclaimUserPrincipal principal;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    @Nullable
    public Object getCredentials() {
        return null;
    }

    @Override
    @Nullable
    public Object getDetails() {
        return null;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return principal.getName();
    }
}
