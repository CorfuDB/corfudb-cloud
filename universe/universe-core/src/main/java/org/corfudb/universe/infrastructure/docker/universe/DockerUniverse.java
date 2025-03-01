package org.corfudb.universe.infrastructure.docker.universe;

import com.github.dockerjava.api.DockerClient;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.common.util.ClassUtils;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.AbstractUniverse;
import org.corfudb.universe.api.universe.Universe;
import org.corfudb.universe.api.universe.UniverseException;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.Group;
import org.corfudb.universe.api.universe.group.GroupParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.NodeParams;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCorfuCluster;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCorfuLongevityCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents Docker implementation of a {@link Universe}.
 */
@Slf4j
public class DockerUniverse extends AbstractUniverse {

    public static class DockerClusters {
        private final Map<ClusterType, DockerClusterBuilder> clusters = new HashMap<>();

        private final DockerClusterBuilder corfu = (groupParams, dockerClient, universeParams, loggingParams) ->
                DockerCorfuCluster.builder()
                        .universeParams(universeParams)
                        .params(ClassUtils.cast(groupParams))
                        .loggingParams(loggingParams)
                        .docker(dockerClient)
                        .build();

        private final DockerClusterBuilder longevity = (groupParams, dockerClient, universeParams, loggingParams) ->
                DockerCorfuLongevityCluster.builder()
                        .universeParams(universeParams)
                        .docker(dockerClient)
                        .containerParams(ClassUtils.cast(groupParams))
                        .loggingParams(loggingParams)
                        .build();

        public void add(ClusterType clusterType, DockerClusterBuilder clusterBuilder) {
            clusters.put(clusterType, clusterBuilder);
        }

        private void addCorfuCluster() {
            add(ClusterType.CORFU, corfu);
        }

        private void addLongevityCluster() {
            add(ClusterType.CORFU_LONGEVITY_APP, longevity);
        }

        public Optional<DockerClusterBuilder> get(ClusterType type) {
            return Optional.ofNullable(clusters.get(type));
        }
    }

    public static final DockerClusters CLUSTERS = new DockerClusters();

    /**
     * Docker parameter --network=host doesn't work in mac machines,
     * FakeDns is used to solve the issue, it resolves a dns record (which is a node name) to loopback address always.
     * See Readme.md
     */
    private static final FakeDns FAKE_DNS = FakeDns.getInstance().install();
    private final DockerClient docker;
    private final DockerNetwork network = new DockerNetwork();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean destroyed = new AtomicBoolean();

    /**
     * Docker universe constructor
     *
     * @param universeParams universe params
     * @param docker         docker client
     * @param loggingParams  logging params
     */
    @Builder
    public DockerUniverse(UniverseParams universeParams, DockerClient docker, LoggingParams loggingParams) {
        super(universeParams, loggingParams);
        this.docker = docker;

        CLUSTERS.addCorfuCluster();
        CLUSTERS.addLongevityCluster();

        init();
    }

    /**
     * Deploy a {@link Universe} according to provided parameter, docker client, docker network, and other components.
     * The instances of this class are immutable. In other word, when the state of an instance is changed a new
     * immutable instance is provided.
     *
     * @return Current instance of a docker {@link Universe} would be returned.
     * @throws UniverseException this exception will be thrown if deploying a {@link Universe} is not successful
     */
    @Override
    public DockerUniverse deploy() {
        log.info("Deploying universe: {}", universeParams);

        if (!initialized.get()) {
            network.setup();
            initialized.set(true);
        }

        deployGroups();

        return this;
    }

    @Override
    public void shutdown() {
        log.info("Shutdown docker universe: {}", universeId.toString());

        if (!universeParams.isCleanUpEnabled()) {
            log.info("Shutdown is disabled");
            return;
        }

        if (destroyed.getAndSet(true)) {
            log.warn("Docker universe already destroyed");
            return;
        }

        shutdownGroups();

        // Remove docker network
        try {
            network.shutdown();
        } catch (UniverseException e) {
            log.debug("Can't remove docker network: {}", universeParams.getNetworkName());
        }
    }

    @Override
    public <P extends NodeParams, D extends DeploymentParams<P>> Universe add(GroupParams<P, D> groupParams) {
        universeParams.add(groupParams);
        buildGroup(groupParams).deploy();
        return this;
    }

    @Override
    protected <P extends NodeParams, D extends DeploymentParams<P>> Group buildGroup(GroupParams<P, D> groupParams) {

        groupParams.getNodesParams().forEach(node ->
                FAKE_DNS.addForwardResolution(node.getApplicationParams().getName(), InetAddress.getLoopbackAddress())
        );

        return CLUSTERS
                .get(groupParams.getType())
                .orElseThrow(() -> new UniverseException("Unknown cluster type: " + groupParams.getType()))
                .build(ClassUtils.cast(groupParams), docker, universeParams, loggingParams);
    }

    @FunctionalInterface
    public interface DockerClusterBuilder {

        Group build(GroupParams<NodeParams, DeploymentParams<NodeParams>> groupParams, DockerClient docker,
                    UniverseParams universeParams, LoggingParams loggingParams);
    }

    private class DockerNetwork {
        private final Logger log = LoggerFactory.getLogger(DockerNetwork.class);
        private String dockerNetworkId;

        /**
         * Sets up a docker network.
         *
         * @throws UniverseException will be thrown if cannot set up a docker network
         */
        void setup() {
            String networkName = universeParams.getNetworkName();
            log.info("Setup network: {}", networkName);

            dockerNetworkId = docker.createNetworkCmd()
                    .withAttachable(true)
                    .withName(networkName)
                    .withCheckDuplicate(true)
                    .exec()
                    .getId();

        }

        /**
         * Shuts down a docker network.
         *
         * @throws UniverseException will be thrown if cannot shut up a docker network
         */
        void shutdown() {
            String networkName = universeParams.getNetworkName();
            log.info("Shutdown network: {}", networkName);
            try {
                docker.removeNetworkCmd(dockerNetworkId).exec();
            } catch (Exception e) {
                final String err = String.format("Cannot shutdown docker network: %s.", networkName);
                throw new UniverseException(err, e);
            }
        }
    }
}
