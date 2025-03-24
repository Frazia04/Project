package de.rptu.cs.exclaim.security;

import org.springframework.security.access.AccessDeniedException;

public class NotAuthenticatedException extends AccessDeniedException {
    public NotAuthenticatedException() {
        super("Not authenticated");
    }
}
