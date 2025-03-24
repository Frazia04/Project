package de.rptu.cs.exclaim.runners;

import de.rptu.cs.exclaim.Main;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;


@Component
// No @Order -> implicitly the lowest precedence
@Slf4j
@RequiredArgsConstructor
public class ShutdownRunner implements ApplicationRunner {
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (Main.getShutdown()) {
            log.info("CLI shutdown command present, shutting down...");
            applicationContext.close();
            System.exit(0);
        }
    }
}
