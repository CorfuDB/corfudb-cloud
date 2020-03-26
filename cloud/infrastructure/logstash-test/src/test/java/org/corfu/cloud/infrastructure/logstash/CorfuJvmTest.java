package org.corfu.cloud.infrastructure.logstash;

import org.corfu.cloud.infrastructure.logstash.LogstashConfig.DockerVolume;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.SerializationFeature;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class CorfuJvmTest {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final CorfuJvmConfig jvmConfig = new CorfuJvmConfig();

    @Test
    public void test() throws Exception {

        Files.deleteIfExists(jvmConfig.outputFile);

        try (GenericContainer<?> logstash = new GenericContainer<>(jvmConfig.logstashCfg.logstashImage)) {

            resourceMapping(logstash, jvmConfig.logstashCfg.patterns());
            resourceMapping(logstash, jvmConfig.logstashCfg.templates());
            resourceMapping(logstash, jvmConfig.logstashCfg.logstashConf());
            resourceMapping(logstash, jvmConfig.logstashCfg.logstashYml());
            resourceMapping(logstash, jvmConfig.corfuJvmConf);
            resourceMapping(logstash, jvmConfig.inputLog);

            logstash
                    //logstash output
                    .withFileSystemBind("build/test-output", "/logstash-test-output", BindMode.READ_WRITE)
                    .withCommand("/bin/sh", "-c", "logstash < " + jvmConfig.jvmGcLog)

                    .waitingFor(Wait.forLogMessage(".*Logstash shut down.*", 3))
                    .withStartupTimeout(Duration.ofMinutes(1));
            try {
                logstash.start();
            } catch (Exception ex) {
                fail("Invalid logstash config");
            }

            Integer exitCode = logstash.getCurrentContainerInfo().getState().getExitCode();
            assertEquals(0, exitCode);

            //compare output with expected result
            List<String> output = Files.readAllLines(jvmConfig.outputFile);
            URI expectedOutputUri = getClass().getClassLoader().getResource("corfu-jvm/output.log").toURI();
            List<String> expectedOutput = Files.readAllLines(Paths.get(expectedOutputUri));

            Collections.sort(output);
            Collections.sort(expectedOutput);

            for (int i = 0; i < expectedOutput.size(); i++) {
                String expectedLine = expectedOutput.get(i);
                String actualLine = output.get(i);

                JsonNode expectedJson = mapper.readTree(expectedLine);
                JsonNode actualJson = mapper.readTree(actualLine);

                Iterator<Map.Entry<String, JsonNode>> iter = expectedJson.fields();
                while (iter.hasNext()) {
                    Map.Entry<String, JsonNode> expectedEntry = iter.next();
                    if (expectedEntry.getKey().equals("host") || expectedEntry.getKey().equals("@timestamp")) {
                        continue;
                    }

                    assertThat(expectedEntry.getValue()).isEqualTo(actualJson.get(expectedEntry.getKey()));
                }
            }
        }
    }

    private void resourceMapping(GenericContainer<?> logstash, DockerVolume vol) {
        logstash.withClasspathResourceMapping(
                vol.resourcePath().toString(), vol.containerPath().toString(), vol.mode()
        );
    }

    public static class CorfuJvmConfig {
        private final LogstashConfig logstashCfg = ImmutableLogstashConfig.builder().build();

        private final Path outputFile = Paths.get("build", "test-output", "output.log");

        private final DockerVolume corfuJvmConf = ImmutableDockerVolume.of(
                Paths.get("pipeline/corfu-jvm.conf"),
                logstashCfg.pipeline().containerPath().resolve("corfu-jvm.conf"),
                BindMode.READ_ONLY
        );

        private final String jvmGcLog = "/var/log/corfu/jvm/corfu.jvm.gc.9000.log.0";

        private final DockerVolume inputLog = ImmutableDockerVolume.of(
                Paths.get("corfu-jvm/input.log"),
                Paths.get(jvmGcLog),
                BindMode.READ_ONLY
        );
    }
}