package de.rptu.cs.exclaim;

import de.rptu.cs.exclaim.data.records.UserRecord;
import de.rptu.cs.exclaim.security.ExclaimPasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static de.rptu.cs.exclaim.schema.tables.Users.USERS;

@RequiredArgsConstructor
public class LoginTest extends ExclaimTestConfiguration {
    private final DSLContext ctx;
    private final ExclaimPasswordEncoder exclaimPasswordEncoder;

    @Test
    @RequiresCleanDatabase
    @MutatesDatabase
    void testLogin() {
        UserRecord userRecord = ctx.newRecord(USERS);
        userRecord.setUsername("Testuser");
        userRecord.setFirstname("Test");
        userRecord.setLastname("Test");
        userRecord.setEmail("test@example.com");
        userRecord.setPassword(exclaimPasswordEncoder.encode("my-password"));
        userRecord.insert();
        driver.get(url());
        WebElement usernameElement = wait.until(driver -> driver.findElement(By.id("username")));
        WebElement passwordElement = driver.findElement(By.id("password"));
        usernameElement.clear();
        usernameElement.sendKeys("Testuser");
        passwordElement.clear();
        passwordElement.sendKeys("my-password");
        passwordElement.submit();
        wait.until(driver -> driver.findElement(By.cssSelector("main.home")));
    }
}
