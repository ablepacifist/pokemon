package pokemon;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Proves the layered-env mechanism declared in application.properties
 * (spring.config.import = module .env  &lt;  root .env  &lt;  MASTER_ENV_FILE
 * &lt; real env/system properties) WITHOUT starting the Spring application
 * context, the embedded web server, or opening the database: it applies
 * Spring Boot's config-data processing directly to a bare
 * StandardEnvironment, the same mechanism SpringApplication uses
 * internally, with no @Component scanning and no beans created.
 *
 * "spring.config.name" is pointed at a name that does not exist, so this
 * test never touches the real application.properties (or any real .env)
 * on the box - only synthetic temp files/keys are used. A real OS
 * environment variable cannot be set for the current process from within
 * a running JVM, so System.setProperty stands in for it: Spring's default
 * environment gives "systemProperties" the same top-of-the-stack
 * precedence as "systemEnvironment" - both rank above any config-data
 * file - so this faithfully exercises rule 3 of the layering (real
 * process env vars beat every file).
 */
class LayeredEnvConfigTest {

    private static final String KEY = "POKEMONSERVER_LAYERING_TEST_KEY";
    private static final String MASTER_VAR = "POKEMONSERVER_LAYERING_TEST_MASTER_FILE";

    @AfterEach
    void cleanup() {
        System.clearProperty("spring.config.name");
        System.clearProperty("spring.config.import");
        System.clearProperty(MASTER_VAR);
        System.clearProperty(KEY);
    }

    @Test
    void moduleEnvAloneAppliesWhenMasterFileIsAbsent() throws IOException {
        Path moduleDir = Files.createTempDirectory("pokemonserver-module-");
        Path missingMaster = moduleDir.resolve("no-such-master.env");
        try {
            Files.writeString(moduleDir.resolve(".env"), KEY + "=from-module\n");
            configureImport(moduleDir.resolve(".env"), missingMaster);

            assertEquals("from-module", resolve(KEY));
        } finally {
            deleteQuietly(moduleDir.resolve(".env"));
            deleteQuietly(moduleDir);
        }
    }

    @Test
    void masterEnvFileOverridesModuleEnv() throws IOException {
        Path moduleDir = Files.createTempDirectory("pokemonserver-module-");
        Path masterFile = Files.createTempFile("pokemonserver-master-", ".env");
        try {
            Files.writeString(moduleDir.resolve(".env"), KEY + "=from-module\n");
            Files.writeString(masterFile, KEY + "=from-master\n");
            configureImport(moduleDir.resolve(".env"), masterFile);

            assertEquals("from-master", resolve(KEY));
        } finally {
            deleteQuietly(moduleDir.resolve(".env"));
            deleteQuietly(moduleDir);
            deleteQuietly(masterFile);
        }
    }

    @Test
    void realEnvVarBeatsMasterEnvFile() throws IOException {
        Path moduleDir = Files.createTempDirectory("pokemonserver-module-");
        Path masterFile = Files.createTempFile("pokemonserver-master-", ".env");
        try {
            Files.writeString(moduleDir.resolve(".env"), KEY + "=from-module\n");
            Files.writeString(masterFile, KEY + "=from-master\n");
            configureImport(moduleDir.resolve(".env"), masterFile);

            // Stand-in for a real process environment variable (see class javadoc).
            System.setProperty(KEY, "from-real-env");

            assertEquals("from-real-env", resolve(KEY));
        } finally {
            deleteQuietly(moduleDir.resolve(".env"));
            deleteQuietly(moduleDir);
            deleteQuietly(masterFile);
        }
    }

    @Test
    void absentFilesDoNotFailStartup() {
        Path moduleDir = Path.of(System.getProperty("java.io.tmpdir"),
                "pokemonserver-does-not-exist-" + System.nanoTime());
        // Neither the module .env nor the master file exist on disk.
        configureImport(moduleDir.resolve(".env"), moduleDir.resolve("also-missing.env"));

        // Must not throw, and the key is simply absent.
        assertNull(resolve(KEY));
    }

    /** Mirrors application.properties' spring.config.import, with temp absolute paths. */
    private void configureImport(Path moduleEnv, Path masterFile) {
        System.setProperty("spring.config.name", "pokemonserver-layering-test-inert");
        System.setProperty(MASTER_VAR, asLocation(masterFile));
        System.setProperty("spring.config.import",
                "optional:file:" + asLocation(moduleEnv) + "[.properties],"
              + "optional:file:${" + MASTER_VAR + ":" + asLocation(moduleEnv) + "}[.properties]");
    }

    private static String asLocation(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String resolve(String key) {
        StandardEnvironment env = new StandardEnvironment();
        ConfigDataEnvironmentPostProcessor.applyTo(env);
        return env.getProperty(key);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
