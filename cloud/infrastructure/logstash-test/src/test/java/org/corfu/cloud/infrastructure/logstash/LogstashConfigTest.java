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

    private static final LogstashConfigParameters params = new LogstashConfigParameters();

    @Test
    public void testCorfuConfig() {

        try (GenericContainer<?> logstash = new GenericContainer<>(params.logstashImage)) {

            logstash
                    .withClasspathResourceMapping("pipeline", params.getPipelineDir(), BindMode.READ_ONLY)
                    .withClasspathResourceMapping("patterns", params.getPatternsDir(), BindMode.READ_ONLY)
                    .withClasspathResourceMapping("templates", params.getTemplatesDir(), BindMode.READ_ONLY)

                    .withCommand(
                            "logstash", "--log.level=error", "--config.test_and_exit", "-f", params.getCorfuConfig()
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

    private static class LogstashConfigParameters {
        private final String logstashImage = "docker.elastic.co/logstash/logstash:7.6.0";

        private final Path logstashDir = Paths.get("/usr/share/logstash");

        private final Path pipelineDir = logstashDir.resolve("pipeline");
        private final Path patternsDir = logstashDir.resolve("patterns");
        private final Path templatesDir = logstashDir.resolve("templates");
        private final Path corfuConfig = pipelineDir.resolve("corfu.conf");

        public String getPipelineDir() {
            return pipelineDir.toString();
        }

        public String getPatternsDir() {
            return patternsDir.toString();
        }

        public String getTemplatesDir() {
            return templatesDir.toString();
        }

        public String getCorfuConfig() {
            return corfuConfig.toString();
        }
    }
}
