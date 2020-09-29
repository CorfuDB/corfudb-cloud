package org.corfudb.universe.infrastructure.docker.universe;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.NetworkConfig;
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
import org.corfudb.universe.api.universe.node.NodeParams;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCassandraCluster;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCorfuCluster;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerMangleCluster;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerPrometheusCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents Docker implementation of a {@link Universe}.
 */
@Slf4j
public class DockerUniverse extends AbstractUniverse {
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

        switch (groupParams.getType()) {
            case CORFU:
                return DockerCorfuCluster.builder()
                        .universeParams(universeParams)
                        .params(ClassUtils.cast(groupParams))
                        .loggingParams(loggingParams)
                        .docker(docker)
                        .build();
            case PROM:
                return DockerPrometheusCluster.builder()
                        .universeParams(universeParams)
                        .supportParams(ClassUtils.cast(groupParams))
                        .docker(docker)
                        .build();
            case CASSANDRA:
                return DockerCassandraCluster.builder()
                        .universeParams(universeParams)
                        .cassandraParams(ClassUtils.cast(groupParams))
                        .docker(docker)
                        .build();
            case MANGLE:
                return DockerMangleCluster.builder()
                        .universeParams(universeParams)
                        .docker(docker)
                        .containerParams(ClassUtils.cast(groupParams))
                        .build();
            default:
                throw new UniverseException("Unknown node type");
        }
    }

    private class DockerNetwork {
        private final Logger log = LoggerFactory.getLogger(DockerNetwork.class);

        /**
         * Sets up a docker network.
         *
         * @throws UniverseException will be thrown if cannot set up a docker network
         */
        void setup() {
            String networkName = universeParams.getNetworkName();
            log.info("Setup network: {}", networkName);
            NetworkConfig networkConfig = NetworkConfig.builder()
                    .checkDuplicate(true)
                    .attachable(true)
                    .name(networkName)
                    .build();

            try {
                docker.createNetwork(networkConfig);
            } catch (Exception e) {
                throw new UniverseException("Cannot setup docker network.", e);
            }
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
                docker.removeNetwork(networkName);
            } catch (Exception e) {
                final String err = String.format("Cannot shutdown docker network: %s.", networkName);
                throw new UniverseException(err, e);
            }
        }
    }
}
