package org.corfudb.universe.infrastructure.docker.universe.node.server;

import com.spotify.docker.client.DockerClient;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.api.universe.node.NodeException;
import org.corfudb.universe.api.universe.node.NodeParams;
import org.corfudb.universe.infrastructure.docker.DockerManager;

import java.time.Duration;

/**
 * A unit of a node deployment to docker. Deploys docker containers
 * @param <P> node params
 */
@Builder
public class DockerNode<P extends NodeParams> implements Node<P, DockerNode<P>> {
    @Getter
    @NonNull
    private final P appParams;

    @NonNull
    @Getter
    private final DockerContainerParams<P> containerParams;

    @NonNull
    private final DockerManager<P> dockerManager;

    @NonNull
    private final DockerClient docker;

    @NonNull
    private final GenericGroupParams<P, DockerContainerParams<P>> groupParams;

    /**
     * Deploys a docker container
     * @return docker node
     */
    @Override
    public DockerNode<P> deploy() {
        dockerManager.deployContainer();
        return this;
    }

    /**
     * This method attempts to gracefully stop the Corfu server. After timeout, it will kill the Corfu server.
     *
     * @param timeout a duration after which the stop will kill the server
     * @throws NodeException this exception will be thrown if the server cannot be stopped.
     */
    @Override
    public DockerNode<P> stop(Duration timeout) {
        dockerManager.stop(timeout);
        return this;
    }

    /**
     * Immediately kill the Corfu server.
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public DockerNode<P> kill() {
        dockerManager.kill();
        return this;
    }

    /**
     * Immediately kill and remove the docker container
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public DockerNode<P> destroy() {
        dockerManager.destroy();
        return this;
    }

    @Override
    public P getParams() {
        return appParams;
    }

    @Override
    public IpAddress getNetworkInterface() {
        return IpAddress.builder().ip(appParams.getName()).build();
    }
}
