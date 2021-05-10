package org.corfudb.universe.api.universe.group.cluster;

import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.group.Group;
import org.corfudb.universe.api.universe.group.GroupParams;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.api.universe.node.NodeParams;

public interface Cluster<
        P extends NodeParams,
        D extends DeploymentParams<P>,
        T extends Node<P>,
        G extends GroupParams<P, D>
        > extends Group<P, D, T, G> {

    /**
     * Bootstrap a {@link Cluster}
     */
    void bootstrap();

    /**
     * Bootstrap a {@link Cluster}
     * @param force forcefully
     */
    void bootstrap(boolean force);

    enum ClusterType {
        CORFU, CORFU_LONGEVITY_APP, PROM, CASSANDRA, MANGLE
    }
}
