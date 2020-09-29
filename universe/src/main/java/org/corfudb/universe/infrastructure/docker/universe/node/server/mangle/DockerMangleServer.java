package org.corfudb.universe.infrastructure.docker.universe.node.server.mangle;

import com.spotify.docker.client.DockerClient;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.api.universe.node.NodeException;
import org.corfudb.universe.infrastructure.docker.DockerManager;
import org.corfudb.universe.universe.node.server.mangle.MangleServerParams;

import java.time.Duration;

@Slf4j
@Builder
public class DockerMangleServer implements Node<MangleServerParams, DockerMangleServer> {

    @Getter
    @NonNull
    private final MangleServerParams params;

    @NonNull
    @Getter
    private final DockerContainerParams<MangleServerParams> containerParams;

    @NonNull
    private final DockerManager<MangleServerParams> dockerManager;

    @NonNull
    private final DockerClient docker;

    @NonNull
    private final GenericGroupParams<MangleServerParams, DockerContainerParams<MangleServerParams>> clusterParams;

    @Override
    public DockerMangleServer deploy() {
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
    public DockerMangleServer stop(Duration timeout) {
        dockerManager.stop(timeout);
        return this;
    }

    /**
     * Immediately kill the Corfu server.
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public DockerMangleServer kill() {
        dockerManager.kill();
        return this;
    }

    /**
     * Immediately kill and remove the docker container
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public DockerMangleServer destroy() {
        dockerManager.destroy();
        return this;
    }

    @Override
    public IpAddress getNetworkInterface() {
        return IpAddress.builder().ip(params.getName()).build();
    }

}
