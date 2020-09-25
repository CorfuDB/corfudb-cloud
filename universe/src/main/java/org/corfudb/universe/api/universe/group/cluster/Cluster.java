package org.corfudb.universe.api.universe.group.cluster;

import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.group.Group;
import org.corfudb.universe.api.universe.group.Group.GroupParams;
import org.corfudb.universe.api.universe.node.Node;

public interface Cluster<
        P extends Node.NodeParams,
        D extends DeploymentParams<P>,
        T extends Node<P, T>,
        G extends GroupParams<P, D>
        > extends Group<P, D, T, G> {

    /**
     * Bootstrap a {@link Cluster}
     */
    void bootstrap();

    enum ClusterType {
        CORFU_CLUSTER, SUPPORT_CLUSTER
    }
}
