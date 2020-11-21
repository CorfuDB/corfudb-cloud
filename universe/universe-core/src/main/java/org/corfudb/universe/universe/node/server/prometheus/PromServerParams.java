package org.corfudb.universe.universe.node.server.prometheus;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.universe.node.CommonNodeParams;
import org.corfudb.universe.api.universe.node.NodeParams;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Builder
@EqualsAndHashCode
@ToString
public class PromServerParams implements NodeParams {

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
}
