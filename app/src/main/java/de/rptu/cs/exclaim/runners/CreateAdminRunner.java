package de.rptu.cs.exclaim.runners;

import de.rptu.cs.exclaim.Main;
import de.rptu.cs.exclaim.data.records.UserRecord;
import de.rptu.cs.exclaim.security.ExclaimPasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import static de.rptu.cs.exclaim.schema.tables.Users.USERS;

@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class CreateAdminRunner implements ApplicationRunner {
    private final ExclaimPasswordEncoder pe;
    private final TransactionTemplate transactionTemplate;
    private final DSLContext ctx;

    @Override
    public void run(ApplicationArguments args) {
        if (!Main.admin.isEmpty()) {
            transactionTemplate.execute(transactionStatus -> {
                for (Main.Admin options : Main.admin) {
                    UserRecord userRecord = ctx.newRecord(USERS);
                    userRecord.setUsername(options.getUsername());
                    userRecord.setPassword(pe.encode(options.getPassword()));
                    userRecord.setAdmin(true);
                    userRecord.setFirstname("Admin");
                    userRecord.setLastname("Admin");
                    userRecord.setEmail("admin@example.com");
                    log.info("Creating admin user {}", userRecord);
                    try {
                        userRecord.insert();
                        log.info("Successfully created user {}", userRecord);
                    } catch (DataAccessException e) {
                        log.error("Could not create admin user!", e);
                        if (Main.getShutdown()) {
                            throw e;
                        }
                    }
                }
                return null;
            });
        }
    }
}
