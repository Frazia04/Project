package de.rptu.cs.exclaim.frontend;

import com.fasterxml.jackson.databind.ObjectWriter;
import de.rptu.cs.exclaim.api.FELoginSuccess;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.security.UserWithPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class FrontendAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private final AccessChecker accessChecker;
    private final ObjectWriter objectWriter;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        UserWithPermissions userWithPermissions = accessChecker.getUserWithPermissionsOpt().orElseThrow(
            () -> new IllegalStateException("Missing authentication after successful login")
        );

        String csrf = null;
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrfToken) {
            csrf = csrfToken.getToken();
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectWriter.writeValue(response.getOutputStream(), new FELoginSuccess(
            csrf,
            FrontendData.accountData(userWithPermissions)
        ));
    }
}
