package de.rptu.cs.exclaim.frontend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.security.UserWithPermissions;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controller to serve the static {@code index.html} file.
 * <p>
 * It does not just serve the static file, it adapts it as follows:
 * <ul>
 * <li>Fix the {@code <base>} tag depending on the configured context path
 * <li>inject {@code <link rel="modulepreload" ...>} tags depending on the highest permission of the requesting user
 * <li>inject {@code <script>} tag to set {@code window.csrfToken} and {@code window.accountData}
 * </ul>
 */
@Controller
@Slf4j
public class FrontendController {
    static final String INDEX_PATH = "/index.html";

    private enum Role {
        // In ascending privilege order
        GUEST, STUDENT, TUTOR, ASSISTANT, ADMIN
    }

    private final ObjectWriter objectWriter;
    private final AccessChecker accessChecker;
    private final FrontendConfiguration frontendConfiguration;
    private final String sourceBeforeEndHead;
    private final String sourceAfterEndHead;
    private final Map<Role, String> preloads;

    public FrontendController(
        FrontendConfiguration frontendConfiguration,
        AccessChecker accessChecker,
        ObjectWriter objectWriter,
        ObjectReader objectReader,
        ServletContext servletContext
    ) throws IOException {
        this.frontendConfiguration = frontendConfiguration;
        this.accessChecker = accessChecker;
        this.objectWriter = objectWriter;

        // Load the preload tags per role
        this.preloads = collectPreloads(objectReader);

        // Load the source and split it at </head>
        String source = loadIndexHtml(servletContext);
        int endHeadIndex = source.indexOf("</head>");
        this.sourceBeforeEndHead = source.substring(0, endHeadIndex);
        this.sourceAfterEndHead = source.substring(endHeadIndex);
    }

    @GetMapping(INDEX_PATH)
    @ResponseBody
    public String getFrontend(HttpServletRequest request) throws JsonProcessingException {
        Optional<UserWithPermissions> user = accessChecker.getUserWithPermissionsOpt();
        log.debug("Frontend index.html request by user: {}", user);
        return sourceBeforeEndHead
            + preloadsForUser(user)
            + "<script>"
            + frontendConfiguration()
            + csrfToken(request)
            + accountData(user)
            + "</script>"
            + sourceAfterEndHead;
    }

    private static String loadIndexHtml(ServletContext servletContext) throws IOException {
        // Load index.html
        String source = new ClassPathResource("static/index.html", FrontendController.class.getClassLoader())
            .getContentAsString(StandardCharsets.UTF_8);

        // Update <base> tag to reflect contextPath
        String contextPath = servletContext.getContextPath();
        if (!contextPath.isEmpty()) {
            source = source.replace("<base href=\"/\">", "<base href=\"" + contextPath + "/\">");
        }

        return source;
    }

    @SuppressWarnings("EnumOrdinal")
    private static Map<Role, String> collectPreloads(ObjectReader objectReader) throws IOException {
        // Collect all module files relevant to each role. The array is indexed with Role's ordinal.
        @SuppressWarnings({"unchecked", "rawtypes"})
        Set<String>[] filesPerRole = new Set[Role.values().length];
        for (int i = 0; i < filesPerRole.length; i++) {
            filesPerRole[i] = new HashSet<>();
        }
        Pattern filenamePattern = Pattern.compile("^assets/(.+)-\\w+\\.js$");
        try (InputStream inputStream = new ClassPathResource("frontend-manifest.json").getInputStream()) {
            JsonNode root = objectReader.readTree(inputStream);
            for (JsonNode node : root) {
                String file = node.get("file").asText();
                Matcher matcher = filenamePattern.matcher(file);
                if (matcher.matches()) {
                    for (String part : matcher.group(1).split("_", -1)) {
                        for (Role role : Role.values()) {
                            if (role.name().equalsIgnoreCase(part)) {
                                filesPerRole[role.ordinal()].add(file);
                            }
                        }
                    }
                }
            }
        }

        // Transitively add files to higher roles
        for (int i = 1; i < filesPerRole.length; i++) {
            filesPerRole[i].addAll(filesPerRole[i - 1]);
        }

        // Construct the <link> tags for preloads of each role
        Map<Role, String> preloads = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            StringBuilder sb = new StringBuilder();
            for (String file : filesPerRole[role.ordinal()]) {
                sb
                    .append("<link rel=\"modulepreload\" href=\"")
                    .append(file)
                    .append("\"/>");
            }
            preloads.put(role, sb.toString());
        }
        log.debug("Preloads: {}", preloads);
        return preloads;
    }

    @SuppressWarnings("NullAway")
    private String preloadsForUser(Optional<UserWithPermissions> userWithPermissionsOpt) {
        return preloads.get(
            userWithPermissionsOpt
                .map(userWithPermissions ->
                    userWithPermissions.getUser().getAdmin() ? Role.ADMIN
                        : userWithPermissions.getIsAssistantForAnyExercise() ? Role.ASSISTANT
                        : userWithPermissions.getIsTutorForAnyExercise() ? Role.TUTOR
                        : Role.STUDENT
                )
                .orElse(Role.GUEST)
        );
    }

    private String frontendConfiguration() throws JsonProcessingException {
        return "window.frontendConfiguration=" + objectWriter.writeValueAsString(
            frontendConfiguration.getConfiguration()
        ) + ";";
    }

    private String csrfToken(HttpServletRequest request) throws JsonProcessingException {
        return request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrfToken
            ? "window.csrfToken=" + objectWriter.writeValueAsString(csrfToken.getToken()) + ";"
            : "";
    }

    private String accountData(Optional<UserWithPermissions> userWithPermissionsOpt) throws JsonProcessingException {
        return "window.accountData=" + objectWriter.writeValueAsString(
            userWithPermissionsOpt.map(FrontendData::accountData).orElse(null)
        ) + ";";
    }
}
