package org.corfudb.universe.api.group.cluster;

import org.corfudb.universe.api.group.Group;
import org.corfudb.universe.api.group.Group.GroupParams;
import org.corfudb.universe.api.node.Node;

public interface Cluster<T extends Node, G extends GroupParams> extends Group<T, G> {

    /**
     * Bootstrap a {@link Cluster}
     */
    void bootstrap();

    enum ClusterType {
        CORFU_CLUSTER, SUPPORT_CLUSTER
    }
}
