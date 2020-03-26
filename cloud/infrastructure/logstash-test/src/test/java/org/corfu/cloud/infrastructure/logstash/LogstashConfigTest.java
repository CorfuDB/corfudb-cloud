package org.corfu.cloud.infrastructure.logstash;

import org.corfu.cloud.infrastructure.logstash.LogstashConfig.DockerVolume;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class LogstashConfigTest {

    private final LogstashConfig cfg = ImmutableLogstashConfig.builder().build();

    @Test
    public void testCorfuConfig() {

        try (GenericContainer<?> logstash = new GenericContainer<>(LogstashConfig.logstashImage)) {

            classpathResourceMapping(logstash, cfg.pipeline());
            classpathResourceMapping(logstash, cfg.patterns());
            classpathResourceMapping(logstash, cfg.templates());

            String corfuConfig = LogstashConfig.logstashDir.resolve("corfu.conf").toString();

            logstash
                    .withCommand("logstash", "--log.level=error", "--config.test_and_exit", "-f", corfuConfig)
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

    private void classpathResourceMapping(GenericContainer<?> logstash, DockerVolume vol) {
        logstash.withClasspathResourceMapping(
                vol.resourcePath().toString(), vol.containerPath().toString(), vol.mode()
        );
    }
}
