package org.corfudb.universe.node.server;

import com.google.common.collect.ImmutableSortedSet;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.universe.logging.LoggingParams;
import org.corfudb.universe.node.client.LocalCorfuClient;
import org.corfudb.universe.api.universe.UniverseParams;

@Slf4j
@Getter
public abstract class AbstractCorfuServer<T extends CorfuServerParams, U extends UniverseParams>
        implements CorfuServer {

    @NonNull
    protected final T params;

    @NonNull
    protected final U universeParams;

    @NonNull
    protected final LoggingParams loggingParams;

    protected AbstractCorfuServer(@NonNull T params, @NonNull U universeParams,
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

    @Override
    public int compareTo(CorfuServer other) {
        return Integer.compare(getParams().getPort(), other.getParams().getPort());
    }
}
