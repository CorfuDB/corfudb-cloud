package org.corfu.cloud.infrastructure.logstash;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class LogstashConfigTest {
    private static final String LOGSTASH_IMAGE = "docker.elastic.co/logstash/logstash:7.6.0";

    private static final Path LOGSTASH_DIR = Paths.get("/usr/share/logstash");

    private static final Path PIPELINE_DIR = LOGSTASH_DIR.resolve("pipeline");
    private static final Path PATTERNS_DIR = LOGSTASH_DIR.resolve("patterns");
    private static final Path TEMPLATES_DIR = LOGSTASH_DIR.resolve("templates");
    private static final Path CORFU_CONFIG = PIPELINE_DIR.resolve("corfu.conf");

    @Test
    public void testCorfuConfig() {

        try (GenericContainer<?> logstash = new GenericContainer<>(LOGSTASH_IMAGE)) {

            logstash
                    .withClasspathResourceMapping("pipeline", PIPELINE_DIR.toString(), BindMode.READ_ONLY)
                    .withClasspathResourceMapping("patterns", PATTERNS_DIR.toString(), BindMode.READ_ONLY)
                    .withClasspathResourceMapping("templates", TEMPLATES_DIR.toString(), BindMode.READ_ONLY)

                    .withCommand(
                            "logstash", "--log.level=error", "--config.test_and_exit", "-f", CORFU_CONFIG.toString()
                    )

                    .waitingFor(Wait.forLogMessage(".*Configuration OK.*", 1))
                    .withStartupTimeout(Duration.ofMinutes(1));

            try {
                logstash.start();
            } catch (Exception ex) {
                fail("Invalid logstash config");
            }

            Integer exitCode = logstash.getCurrentContainerInfo().getState().getExitCode();
            assertEquals(0, exitCode);
        }
    }
}
