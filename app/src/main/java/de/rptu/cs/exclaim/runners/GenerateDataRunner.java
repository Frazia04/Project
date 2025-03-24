package de.rptu.cs.exclaim.runners;

import de.rptu.cs.exclaim.Main;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@Slf4j
public class GenerateDataRunner implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        for (Main.Generate options : Main.generate) {
            log.info("Generating data... {}", options.toString());
            log.error("Data generation not yet implemented!");
            // TODO: implement data generation
        }
    }
}
