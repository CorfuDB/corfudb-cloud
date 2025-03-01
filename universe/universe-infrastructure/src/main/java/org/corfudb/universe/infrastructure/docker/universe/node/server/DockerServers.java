package org.corfudb.universe.infrastructure.docker.universe.node.server;

import com.github.dockerjava.api.model.Network;
import lombok.Builder;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.universe.node.NodeException;
import org.corfudb.universe.infrastructure.docker.universe.node.server.prometheus.PrometheusConfig;
import org.corfudb.universe.universe.node.server.cassandra.CassandraServerParams;
import org.corfudb.universe.universe.node.server.mangle.MangleServerParams;
import org.corfudb.universe.universe.node.server.prometheus.PromServerParams;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public interface DockerServers {

    @SuperBuilder
    class DockerMangleServer extends DockerNode<MangleServerParams> {

    }

    @SuperBuilder
    class DockerCassandraServer extends DockerNode<CassandraServerParams> {

    }

    @SuperBuilder
    @Slf4j
    class DockerPrometheusServer extends DockerNode<PromServerParams> {
        private static final String LINUX_OS = "linux";

        @NonNull
        @Builder.Default
        private final List<Path> openedFiles = new ArrayList<>();

        @Override
        public void deploy() {
            PromServerParams appParams = getContainerParams().getApplicationParams();
            createConfiguration(appParams.getCommonParams().getPorts(), appParams.getPrometheusConfigPath());
            super.deploy();
        }

        private void createConfiguration(Set<Integer> metricsPorts, Path tempConfiguration) {
            try {
                String corfuRuntimeIp = "host.docker.internal";

                if (System.getProperty("os.name").equalsIgnoreCase(LINUX_OS)) {
                    corfuRuntimeIp = getDocker()
                            .inspectNetworkCmd()
                            .withNetworkId(getContainerParams().getNetworkName())
                            .exec()
                            .getIpam()
                            .getConfig()
                            .stream()
                            .findFirst()
                            .map(Network.Ipam.Config::getGateway)
                            .orElseThrow(() -> new NodeException("Ip address not found"));
                }

                Files.writeString(
                        tempConfiguration,
                        PrometheusConfig.getConfig(corfuRuntimeIp, metricsPorts)
                );

                openedFiles.add(tempConfiguration);
            } catch (Exception e) {
                throw new NodeException(e);
            }
        }

        /**
         * Immediately kill and remove the docker container
         *
         * @throws NodeException this exception will be thrown if the server can not be killed.
         */
        @Override
        public void destroy() {
            super.destroy();
            openedFiles.forEach(path -> {
                if (!path.toFile().delete()) {
                    log.warn("Can't delete a file: {}", path);
                }
            });
        }
    }
}
