package org.corfu.cloud.infrastructure.logstash;

import org.immutables.value.Value;
import org.testcontainers.containers.BindMode;

import java.nio.file.Path;
import java.nio.file.Paths;

@Value.Immutable
@Value.Style(strictBuilder = true)
public interface LogstashConfig {
    String logstashImage = "docker.elastic.co/logstash/logstash:7.10.0";

    Path logstashDir = Paths.get("/usr/share/logstash");

    @Value.Default
    default DockerVolume pipeline() {
        Path pipeline = Paths.get("pipeline");
        return ImmutableDockerVolume.of(pipeline, logstashDir.resolve(pipeline), BindMode.READ_ONLY);
    }

    @Value.Default
    default DockerVolume patterns() {
        Path patterns = Paths.get("patterns");
        return ImmutableDockerVolume.of(patterns, logstashDir.resolve(patterns), BindMode.READ_ONLY);
    }

    @Value.Default
    default DockerVolume templates() {
        Path templates = Paths.get("templates");
        return ImmutableDockerVolume.of(templates, logstashDir.resolve(templates), BindMode.READ_ONLY);
    }

    @Value.Default
    default DockerVolume logstashYml() {
        Path logstashYml = Paths.get("logstash.yml");
        Path containerYml = logstashDir.resolve("config").resolve(logstashYml);
        return ImmutableDockerVolume.of(logstashYml, containerYml, BindMode.READ_ONLY);
    }

    @Value.Derived
    default DockerVolume logstashConf() {
        return ImmutableDockerVolume.of(
                Paths.get("common-logstash.conf"),
                logstashDir.resolve("pipeline").resolve("logstash.conf"),
                BindMode.READ_ONLY
        );
    }


    @Value.Immutable
    @Value.Style(allParameters = true)
    interface DockerVolume {
        Path resourcePath();

        Path containerPath();

        BindMode mode();
    }
}
