package de.rptu.cs.exclaim.frontend;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * A {@link Filter} that forwards requests matching frontend routes to index.html.
 * Necessary for Single Page Application using the browsers history api.
 */
@Component
@Slf4j
public class FrontendRoutesFilter extends OncePerRequestFilter {
    private final Pattern routesPattern;

    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    public FrontendRoutesFilter() throws IOException {
        routesPattern = Pattern.compile(
            new ClassPathResource("frontend-routes.regexp").getContentAsString(StandardCharsets.UTF_8)
        );
        log.debug("Frontend routes: {}", routesPattern);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if ("GET".equals(request.getMethod())) {
            String path = urlPathHelper.getPathWithinApplication(request);
            if (routesPattern.matcher(path).matches()) {
                log.debug("Request for {} matches frontend route, serving index.html", path);
                request.getServletContext()
                    .getRequestDispatcher(FrontendController.INDEX_PATH)
                    .forward(request, response);

                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
