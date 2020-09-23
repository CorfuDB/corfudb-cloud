package org.corfudb.universe.node.server;

import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.corfudb.universe.api.node.Node.NodeParams;
import org.corfudb.universe.api.node.Node.NodeType;
import org.corfudb.universe.util.IpAddress;
import org.slf4j.event.Level;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Builder
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class SupportServerParams implements NodeParams {

    @Default
    @NonNull
    @Getter
    private final Set<Integer> ports = ImmutableSet.of(9090);

    @Default
    @NonNull
    @Getter
    @EqualsAndHashCode.Exclude
    private final Level logLevel = Level.INFO;

    @NonNull
    @Getter
    private final NodeType nodeType;

    @Getter
    @NonNull
    private final String clusterName;

    @Getter
    @Default
    @NonNull
    @EqualsAndHashCode.Exclude
    private final Duration stopTimeout = Duration.ofSeconds(1);

    @Default
    @Getter
    @NonNull
    private final Path prometheusConfigPath = Paths.get("/etc/prometheus/prometheus.yml");

    @Override
    public String getName() {
        return clusterName + "-support-node-" + getNodeType();
    }

    @Override
    public Optional<String> getCommandLine(IpAddress networkInterface) {
        return Optional.empty();
    }

    public boolean isEnabled() {
        return !ports.isEmpty();
    }
}
