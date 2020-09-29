package org.corfudb.universe.scenario.fixture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.Builder;
import lombok.Builder.Default;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams.PortBinding;
import org.corfudb.universe.api.deployment.vm.VmParams;
import org.corfudb.universe.api.deployment.vm.VmParams.VmName;
import org.corfudb.universe.api.deployment.vm.VmParams.VsphereParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.node.CommonNodeParams;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuClusterParams;
import org.corfudb.universe.universe.node.server.ServerUtil;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams.CorfuServerParamsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Dynamically generates a list of corfu server params, based on corfu cluster parameters.
 */
@Builder
public class FixtureUtil {

    @Default
    private final Optional<Integer> initialPort = Optional.empty();
    private Optional<Integer> currPort;

    /**
     * Generates a list of docker corfu server params
     *
     * @param cluster       corfu cluster
     * @param serverBuilder corfu server builder with predefined parameters
     * @return list of docker corfu server params
     */
    ImmutableList<DockerContainerParams<CorfuServerParams>> buildServers(
            CorfuClusterParams<DockerContainerParams<CorfuServerParams>> cluster,
            CorfuServerParamsBuilder serverBuilder, UniverseParams universeParams) {

        currPort = initialPort;

        List<DockerContainerParams<CorfuServerParams>> serversParams = IntStream
                .rangeClosed(1, cluster.getNumNodes())
                .map(i -> getPort())
                .boxed()
                .sorted()
                .map(port -> {
                    List<PortBinding> ports = ImmutableList.of(new PortBinding(port));
                    CorfuServerParams serverParams = getCorfuServerParams(
                            serverBuilder, port, cluster.getName(), cluster.getServerVersion()
                    );

                    return DockerContainerParams
                            .<CorfuServerParams>builder()
                            .image(CorfuServerParams.DOCKER_IMAGE_NAME)
                            .imageVersion(cluster.getServerVersion())
                            .networkName(universeParams.getNetworkName())
                            .ports(ports)
                            .applicationParams(serverParams)
                            .build();
                })
                .collect(Collectors.toList());

        return ImmutableList.copyOf(serversParams);
    }

    /**
     * Generates a list of VMs corfu server params
     *
     * @param cluster             VM corfu cluster
     * @param serverParamsBuilder corfu server builder with predefined parameters
     * @return list of VM corfu server params
     */
    protected ImmutableList<VmParams<CorfuServerParams>> buildVmServers(
            CorfuClusterParams<VmParams<CorfuServerParams>> cluster,
            CorfuServerParamsBuilder serverParamsBuilder, String vmNamePrefix, VsphereParams vsphereParams) {

        currPort = initialPort;

        List<VmParams<CorfuServerParams>> serversParams = new ArrayList<>();

        for (int i = 0; i < cluster.getNumNodes(); i++) {
            int port = getPort();

            VmName vmName = VmName.builder()
                    .name(vmNamePrefix + (i + 1))
                    .index(i)
                    .build();

            CorfuServerParams serverParams = getCorfuServerParams(
                    serverParamsBuilder, port, cluster.getName(), cluster.getServerVersion()
            );


            VmParams<CorfuServerParams> vmParams = VmParams.<CorfuServerParams>builder()
                    .vmName(vmName)
                    .applicationParams(serverParams)
                    .vsphereParams(vsphereParams)
                    .build();

            serversParams.add(vmParams);
        }

        return ImmutableList.copyOf(serversParams);
    }

    private CorfuServerParams getCorfuServerParams(
            CorfuServerParamsBuilder serverParamsBuilder, int port, String name, String serverVersion) {
        CommonNodeParams commonParams = CommonNodeParams.builder()
                .nodeNamePrefix("corfu")
                .nodeType(Node.NodeType.CORFU_SERVER)
                .clusterName(name)
                .ports(ImmutableSet.of(port))
                .enabled(true)
                .build();

        return serverParamsBuilder
                .commonParams(commonParams)
                .serverVersion(serverVersion)
                .build();
    }

    private int getPort() {
        currPort = currPort.map(oldPort -> oldPort + 1);
        return currPort.orElseGet(ServerUtil::getRandomOpenPort);
    }
}
