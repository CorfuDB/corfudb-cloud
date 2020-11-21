package org.corfudb.universe.api.deployment;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.corfudb.universe.api.universe.node.NodeParams;

/**
 * This interface describes deployment specific parameters.
 * There are different types of deployments out there: docker, vm, processes, they all differ from each other.
 *
 * @param <P> application specific params
 */
public interface DeploymentParams<P extends NodeParams> extends Comparable<DeploymentParams<P>> {

    P getApplicationParams();

    @Override
    default int compareTo(DeploymentParams<P> other) {
        return getApplicationParams().compareTo(other.getApplicationParams());
    }

    @Builder
    @ToString
    class EmptyDeploymentParams<P extends NodeParams> implements DeploymentParams<P> {

        @Getter
        @NonNull
        private final P applicationParams;
    }
}
