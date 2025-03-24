package de.rptu.cs.exclaim.monitoring;

import de.rptu.cs.exclaim.controllers.NotFoundException;
import de.rptu.cs.exclaim.security.AccessChecker;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class ExceptionRegistration {
    private final AccessChecker accessChecker;
    private final MetricsService metrics;

    @ExceptionHandler(Exception.class)
    public void registerException(HttpServletRequest request, Exception e) throws Exception {
        String uri = request.getRequestURI();
        String user = accessChecker.getUserOpt()
            .map(u -> String.format("user id %d (%s %s)", u.getUserId(), u.getFirstname(), u.getLastname()))
            .orElse("anonymous user");
        String userAgent = request.getHeader("User-Agent");
        if (e instanceof AccessDeniedException) {
            Map<String, String[]> params = request.getParameterMap();
            log.warn("Access denied on {} for {}: {}{}", uri, user, e.getMessage(),
                params.isEmpty() ? "" : "\nwith parameters:\n" + params.entrySet().stream()
                    .map(p -> p.getKey() + "=" + String.join(", ", p.getValue()))
                    .collect(Collectors.joining("\n")));
        } else if (e instanceof NotFoundException) {
            log.warn("NotFoundException on {} for {}", uri, user);
        } else if (e instanceof ClientAbortException) {
            log.warn("Connection aborted by client on {} for {} with user agent {}", uri, user, userAgent);
//        } else if (e instanceof MultipartException &&
//                e.getCause() instanceof IllegalStateException &&
//                e.getCause().getCause() instanceof FileUploadBase.SizeLimitExceededException) {
//            FileUploadBase.SizeLimitExceededException limitExceededException = (FileUploadBase.SizeLimitExceededException) e.getCause().getCause();
//            log.warn("Upload size limit exceeded on {} for {} with user agent {}: {} instead of {} bytes", uri, user, userAgent,
//                    limitExceededException.getActualSize(),
//                    limitExceededException.getPermittedSize());
//        } else if (e instanceof MessageDeliveryException
//                && e.getCause() instanceof InvalidCsrfTokenException) {
//            log.warn("Invalid CSRF token on {} for {}", uri, user);
        } else {
            log.error("Exception on {} for {} with user agent {}", uri, user, userAgent, e);
            metrics.registerException();
            // TODO: Using something like Sentry would be nice...
        }
        throw e;
    }
}
