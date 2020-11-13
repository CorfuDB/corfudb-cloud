package org.corfudb.universe.universe.node.server.corfu;

import com.google.common.collect.ImmutableSortedSet;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.universe.node.client.LocalCorfuClient;

@Slf4j
@Getter
public abstract class AbstractCorfuServer implements ApplicationServer {

    @NonNull
    protected final CorfuServerParams params;

    @NonNull
    protected final UniverseParams universeParams;

    @NonNull
    protected final LoggingParams loggingParams;

    protected AbstractCorfuServer(@NonNull CorfuServerParams params, @NonNull UniverseParams universeParams,
                                  @NonNull LoggingParams loggingParams) {
        this.params = params;
        this.universeParams = universeParams;
        this.loggingParams = loggingParams;
    }

    @Override
    public LocalCorfuClient getLocalCorfuClient() {
        return LocalCorfuClient.builder()
                .serverEndpoints(ImmutableSortedSet.of(getEndpoint()))
                .corfuRuntimeParams(CorfuRuntime.CorfuRuntimeParameters.builder())
                .build()
                .deploy();
    }
}
