package de.rptu.cs.exclaim.gradle;

import org.gradle.api.Named;
import org.gradle.api.plugins.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.gradle.api.plugins.JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME;

public enum SqlDialect implements Named {
    Invalid,
    H2,
    PostgreSql;

    @Override
    public String getName() {
        return name();
    }

    public String getSourceSetName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getJarTaskName() {
        return JavaPlugin.JAR_TASK_NAME + name();
    }

    public String getTestSourceSetName() {
        return DEFAULT_TEST_SUITE_NAME + name();
    }

    public static final List<SqlDialect> VALID_DIALECTS = Arrays.stream(SqlDialect.values())
        .filter(d -> d != Invalid)
        .toList();
}
