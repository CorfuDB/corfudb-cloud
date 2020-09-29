package org.corfudb.universe.universe.node.server.mangle;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.universe.node.CommonNodeParams;
import org.corfudb.universe.api.universe.node.NodeParams;

import java.util.Optional;

/**
 * Mangle server configuration parameters
 */
@Builder
@EqualsAndHashCode
@ToString
public class MangleServerParams implements NodeParams {

    @Getter
    @NonNull
    private final CommonNodeParams commonParams;

    @Override
    public Optional<String> getCommandLine(IpAddress networkInterface) {
        return Optional.empty();
    }
}
