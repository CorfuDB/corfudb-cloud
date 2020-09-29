package org.corfudb.universe.infrastructure.vm.universe;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
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
import org.corfudb.universe.infrastructure.vm.universe.group.cluster.VmCorfuCluster;

import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Represents VM implementation of a {@link Universe}.
 * <p>
 * The following are the main functionalities provided by this class:
 * </p>
 * DEPLOY: first deploys VMs on vSphere (if not exist), then deploys the group (corfu server) on the VMs
 * SHUTDOWN: stops the {@link Universe}, i.e. stops the existing {@link Group} gracefully within the provided timeout
 */
@Slf4j
public class VmUniverse extends AbstractUniverse {

    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    @NonNull
    @Getter
    private final ApplianceManager applianceManager;

    /**
     * VmUniverse constructor
     *
     * @param universeParams   universe params
     * @param applianceManager appliance manager
     * @param loggingParams    logging params
     */
    @Builder
    public VmUniverse(UniverseParams universeParams, ApplianceManager applianceManager,
                      LoggingParams loggingParams) {
        super(universeParams, loggingParams);
        this.applianceManager = applianceManager;

        applianceManager.deploy();
        init();
    }

    /**
     * Deploy a vm specific {@link Universe} according to provided parameter, vSphere APIs, and other components.
     *
     * @return Current instance of a VM {@link Universe} would be returned.
     * @throws UniverseException this exception will be thrown if deploying a {@link Universe} is not successful
     */
    @Override
    public VmUniverse deploy() {
        log.info("Deploy the universe: {}", universeId);

        deployGroups();

        return this;
    }

    /**
     * Deploy a {@link Group} on existing VMs according to input parameter.
     */
    @Override
    protected <P extends NodeParams, D extends DeploymentParams<P>> Group buildGroup(GroupParams<P, D> groupParams) {

        if (groupParams.getType() != ClusterType.CORFU) {
            throw new UniverseException("Unknown node type: " + groupParams.getType());
        }

        return VmCorfuCluster.builder()
                .universeParams(universeParams)
                .corfuClusterParams(ClassUtils.cast(groupParams))
                .vms(applianceManager.getVms())
                .loggingParams(loggingParams)
                .build();

    }

    /**
     * Shutdown the {@link Universe} by stopping each of its {@link Group}.
     */
    @Override
    public void shutdown() {
        if (!universeParams.isCleanUpEnabled()) {
            log.info("Shutdown is disabled");
            return;
        }

        if (destroyed.getAndSet(true)) {
            log.info("Can't shutdown vm universe. Already destroyed");
            return;
        }

        log.info("Shutdown the universe: {}, params: {}", universeId, groups);
        shutdownGroups();
    }

    @Override
    public Universe add(GroupParams groupParams) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
