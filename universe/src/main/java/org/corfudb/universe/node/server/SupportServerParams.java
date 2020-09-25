package org.corfudb.universe.node.server;

import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.corfudb.universe.api.node.Node.NodeParams;
import org.corfudb.universe.util.IpAddress;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@SuperBuilder
@EqualsAndHashCode
@ToString
public class SupportServerParams implements NodeParams {

    @Getter
    @NonNull
    private final CommonNodeParams commonParams;

    @Default
    @Getter
    @NonNull
    private final Path prometheusConfigPath = Paths.get("/etc/prometheus/prometheus.yml");

    @Override
    public Optional<String> getCommandLine(IpAddress networkInterface) {
        return Optional.empty();
    }

    public boolean isEnabled() {
        return !commonParams.getPorts().isEmpty();
    }
}
