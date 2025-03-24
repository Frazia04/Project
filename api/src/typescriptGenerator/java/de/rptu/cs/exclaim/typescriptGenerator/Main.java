package de.rptu.cs.exclaim.typescriptGenerator;

import cz.habarta.typescript.generator.Input;
import cz.habarta.typescript.generator.JsonLibrary;
import cz.habarta.typescript.generator.Logger;
import cz.habarta.typescript.generator.OptionalPropertiesDeclaration;
import cz.habarta.typescript.generator.Output;
import cz.habarta.typescript.generator.Settings;
import cz.habarta.typescript.generator.TypeScriptFileType;
import cz.habarta.typescript.generator.TypeScriptGenerator;
import cz.habarta.typescript.generator.TypeScriptOutputKind;
import org.springframework.lang.Nullable;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Main class that is invoked by the :api:generateTypescriptApi Gradle task.
 */
public class Main {
    public static final String BASE_PACKAGE = "de.rptu.cs.exclaim.api";

    public static void main(String[] args) {
        // Load property passed from Gradle build
        String outputFile = Objects.requireNonNull(System.getProperty("exclaim.output_file"));
        String gradleLogLevel = Objects.requireNonNull(System.getProperty("exclaim.log_level"));

        // Set log level to the one used by Gradle
        TypeScriptGenerator.setLogger(new Logger(
            switch (gradleLogLevel) {
                case "DEBUG" -> Logger.Level.Debug;
                case "INFO" -> Logger.Level.Info;
                // case "WARN" -> Logger.Level.Warning;
                case "ERROR" -> Logger.Level.Error;
                default -> Logger.Level.Warning;
            }
        ));

        // Settings, see http://www.habarta.cz/typescript-generator/maven/typescript-generator-maven-plugin/generate-mojo.html
        Settings settings = new Settings();
        settings.jsonLibrary = JsonLibrary.jackson2;
        settings.outputKind = TypeScriptOutputKind.module;
        settings.outputFileType = TypeScriptFileType.implementationFile;
        settings.optionalAnnotations = List.of(Nullable.class);
        settings.optionalPropertiesDeclaration = OptionalPropertiesDeclaration.nullableType;
        settings.noFileComment = true; // timestamp would make build non-deterministic
        settings.customTypeNamingFunction = "name => " +
            // remove base package prefix
            "(name.startsWith('" + BASE_PACKAGE + ".') ? name.substring(" + (BASE_PACKAGE.length() + 1) + ") : name)" +
            // remove FE prefix for base classes
            ".replace(/^FE(?!$)/, '')" +
            // replace special characters with underscore
            ".replace(/\\W/, '_')";

        // TODO: Ability to represent Java records as TypeScript tuples https://github.com/vojtechhabarta/typescript-generator/issues/674

        Input.Parameters inputParameters = new Input.Parameters();
        inputParameters.classNamePatterns = List.of(BASE_PACKAGE + ".**");
        new TypeScriptGenerator(settings).generateTypeScript(
            Input.from(inputParameters),
            Output.to(new File(outputFile))
        );
    }
}
