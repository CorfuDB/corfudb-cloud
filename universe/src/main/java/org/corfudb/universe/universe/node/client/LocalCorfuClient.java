package org.corfudb.universe.universe.node.client;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.reflect.TypeToken;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.CorfuRuntime.CorfuRuntimeParameters.CorfuRuntimeParametersBuilder;
import org.corfudb.runtime.collections.CorfuTable;
import org.corfudb.runtime.view.Layout;
import org.corfudb.runtime.view.ManagementView;
import org.corfudb.runtime.view.ObjectsView;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.util.NodeLocator;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.corfudb.runtime.CorfuRuntime.fromParameters;

/**
 * Provides Corfu client (utility class) used in the local machine
 * (in current process) which is basically a wrapper of CorfuRuntime.
 */
@Slf4j
public class LocalCorfuClient implements CorfuClient {
    private final CorfuRuntime runtime;

    @Getter
    private final ClientParams params;

    @Getter
    private final ImmutableSortedSet<String> serverEndpoints;

    /**
     * Creates local corfu client
     *
     * @param params             params
     * @param serverEndpoints    server endpoints
     * @param corfuRuntimeParams runtime params
     */
    @Builder
    public LocalCorfuClient(
            ClientParams params, ImmutableSortedSet<String> serverEndpoints,
            CorfuRuntimeParametersBuilder corfuRuntimeParams) {

        this.params = params;
        this.serverEndpoints = serverEndpoints;

        List<NodeLocator> layoutServers = serverEndpoints.stream()
                .sorted()
                .map(NodeLocator::parseString)
                .collect(Collectors.toList());

        corfuRuntimeParams
                .layoutServers(layoutServers)
                .systemDownHandler(this::systemDownHandler);

        this.runtime = fromParameters(corfuRuntimeParams.build());
    }

    /**
     * Connect corfu runtime to the server
     *
     * @return LocalCorfuClient
     */
    @Override
    public LocalCorfuClient deploy() {
        connect();
        return this;
    }

    /**
     * Shutdown corfu runtime
     *
     * @param timeout a limit within which the method attempts to gracefully stop the client (not used for a client).
     */
    @Override
    public LocalCorfuClient stop(Duration timeout) {
        runtime.shutdown();
        return this;
    }

    /**
     * Shutdown corfu runtime
     */
    @Override
    public LocalCorfuClient kill() {
        runtime.shutdown();
        return this;
    }

    /**
     * Shutdown corfu runtime
     */
    @Override
    public LocalCorfuClient destroy() {
        runtime.shutdown();
        return this;
    }

    @Override
    public IpAddress getNetworkInterface() {
        throw new IllegalStateException("Network interface is not defined");
    }

    @Override
    public <K, V> CorfuTable<K, V> createDefaultCorfuTable(String streamName) {
        return runtime.getObjectsView()
                .build()
                .setTypeToken(new TypeToken<CorfuTable<K, V>>() {
                })
                .setStreamName(streamName)
                .open();
    }

    @Override
    public void connect() {
        runtime.connect();
    }

    @Override
    public CorfuRuntime getRuntime() {
        return runtime;
    }

    @Override
    public Layout getLayout() {
        return runtime.getLayoutView().getLayout();
    }

    @Override
    public ObjectsView getObjectsView() {
        return runtime.getObjectsView();
    }

    @Override
    public ManagementView getManagementView() {
        return runtime.getManagementView();
    }

    @Override
    public void invalidateLayout() {
        runtime.invalidateLayout();
    }

    @Override
    public void shutdown() {
        runtime.shutdown();
    }
}