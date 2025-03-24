package de.rptu.cs.exclaim;

import de.rptu.cs.exclaim.logging.LogbackConfigurer;
import de.rptu.cs.exclaim.utils.ExclaimBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(proxyBeanMethods = false)
@EnableConfigurationProperties(ExclaimProperties.class)
public class ExclaimApplication {
    public static void main(String[] args) {
        // Silence jOOQ
        System.setProperty("org.jooq.no-logo", "true");
        System.setProperty("org.jooq.no-tips", "true");

        // Create the application
        SpringApplication app = new SpringApplication(ExclaimApplication.class);

        // Configure the banner printed on application startup
        app.setBanner(new ExclaimBanner());

        // Configure ApplicationListeners (they cannot be detected by class path scanning because that happens too late)
        app.addListeners(
            new LogbackConfigurer()
        );

        // Let's start!
        app.run(args);
    }
}
