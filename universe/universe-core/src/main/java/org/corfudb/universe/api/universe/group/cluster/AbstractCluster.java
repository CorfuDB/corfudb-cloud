package org.corfudb.universe.api.universe.group.cluster;

import com.google.common.collect.ImmutableSortedMap;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.UniverseException;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.Group;
import org.corfudb.universe.api.universe.group.GroupParams;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.api.universe.node.NodeException;
import org.corfudb.universe.api.universe.node.NodeParams;
import org.corfudb.universe.universe.group.cluster.corfu.AbstractCorfuCluster;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractCluster<
        P extends NodeParams,
        D extends DeploymentParams<P>,
        N extends Node<P>,
        G extends GroupParams<P, D>
        > implements Cluster<P, D, N, G> {

    @Getter
    @NonNull
    protected final G params;

    @NonNull
    protected final UniverseParams universeParams;

    @NonNull
    protected final LoggingParams loggingParams;

    private final ExecutorService executor;

    protected final ConcurrentNavigableMap<String, N> nodes = new ConcurrentSkipListMap<>();

    protected AbstractCluster(G params, UniverseParams universeParams, LoggingParams loggingParams) {
        this.params = params;
        this.universeParams = universeParams;
        this.executor = Executors.newCachedThreadPool();
        this.loggingParams = loggingParams;
    }

    protected void init() {
        params.getNodesParams().forEach(serverParams -> {
            N server = buildServer(serverParams);
            nodes.put(server.getEndpoint(), server);
        });
    }

    /**
     * Deploys a {@link Group}, including the following steps:
     * a) Deploy the Corfu nodes
     * b) Bootstrap all the nodes to form a cluster
     *
     * @return an instance of {@link AbstractCorfuCluster}
     */
    @Override
    public Group<P, D, N, G> deploy() {
        log.info("Deploy a cluster of nodes. Params: {}", params);

        List<CompletableFuture<N>> asyncDeployment = nodes.values().stream()
                .map(this::deployAsync)
                .collect(Collectors.toList());

        asyncDeployment.stream()
                .map(CompletableFuture::join)
                .forEach(server -> log.debug("Corfu server was deployed: {}", server.getParams().getName()));

        try {
            bootstrap();
        } catch (Exception ex) {
            throw new UniverseException("Can't deploy corfu cluster: " + params.getName(), ex);
        }

        return this;
    }

    protected CompletableFuture<N> deployAsync(N server) {
        Supplier<N> deployAction = () -> {
            server.deploy();
            return server;
        };
        return CompletableFuture.supplyAsync(deployAction, executor);
    }

    protected abstract N buildServer(D deploymentParams);

    /**
     * Stop the cluster
     *
     * @param timeout allowed time to gracefully stop the {@link Group}
     */
    @Override
    public void stop(Duration timeout) {
        log.info("Stop corfu cluster: {}", params.getName());

        nodes().values().forEach(node -> {
            try {
                node.stop(timeout);
            } catch (Exception e) {
                log.warn("Can't stop node: {} in group: {}", node.getParams().getName(), getParams().getName(), e);
            }
        });
    }

    /**
     * Attempt to kills all the nodes in arbitrary order.
     */
    @Override
    public void kill() {
        nodes().values().forEach(node -> {
            try {
                node.kill();
            } catch (Exception e) {
                log.warn("Can't kill node: {} in group: {}", node.getParams().getName(), getParams().getName(), e);
            }
        });
    }

    @Override
    public void destroy() {
        log.info("Destroy group: {}", params.getName());

        nodes().values().forEach(node -> {
            try {
                node.destroy();
            } catch (NodeException e) {
                log.warn("Can't destroy node: {} in group: {}", node.getParams().getName(), getParams().getName(), e);
            }
        });
    }

    @Override
    public N add(D deploymentParams) {
        params.add(deploymentParams);

        N server = deployAsync(buildServer(deploymentParams)).join();
        nodes.put(server.getEndpoint(), server);

        return server;
    }

    @Override
    public ImmutableSortedMap<String, N> nodes() {
        return ImmutableSortedMap.copyOf(nodes);
    }
}
