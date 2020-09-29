package org.corfudb.universe.infrastructure.docker.universe.node.server.cassandra;

import com.spotify.docker.client.DockerClient;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.api.universe.node.NodeException;
import org.corfudb.universe.infrastructure.docker.DockerManager;
import org.corfudb.universe.universe.node.server.cassandra.CassandraServerParams;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Builder
public class DockerCassandraServer implements Node<CassandraServerParams, DockerCassandraServer> {

    @Getter
    @NonNull
    protected final CassandraServerParams params;

    @NonNull
    @Getter
    protected final DockerContainerParams<CassandraServerParams> containerParams;

    @NonNull
    private final DockerManager<CassandraServerParams> dockerManager;

    @NonNull
    private final DockerClient docker;

    @NonNull
    private final GenericGroupParams<CassandraServerParams, DockerContainerParams<CassandraServerParams>> clusterParams;

    @NonNull
    @Default
    private final List<Path> openedFiles = new ArrayList<>();

    @Override
    public DockerCassandraServer deploy() {
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
    public DockerCassandraServer stop(Duration timeout) {
        dockerManager.stop(timeout);
        return this;
    }

    /**
     * Immediately kill the Corfu server.
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public DockerCassandraServer kill() {
        dockerManager.kill();
        return this;
    }

    /**
     * Immediately kill and remove the docker container
     *
     * @throws NodeException this exception will be thrown if the server can not be killed.
     */
    @Override
    public DockerCassandraServer destroy() {
        dockerManager.destroy();
        openedFiles.forEach(path -> {
            if (!path.toFile().delete()) {
                log.warn("Can't delete a file: {}", path);
            }
        });

        return this;
    }

    @Override
    public IpAddress getNetworkInterface() {
        return IpAddress.builder().ip(params.getName()).build();
    }

}
