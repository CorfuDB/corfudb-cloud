package org.corfudb.universe.group.cluster.vm;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.BootstrapUtil;
import org.corfudb.runtime.view.Layout;
import org.corfudb.universe.api.deployment.vm.VmParams;
import org.corfudb.universe.api.deployment.vm.VmParams.VmName;
import org.corfudb.universe.api.deployment.vm.VmUniverseParams;
import org.corfudb.universe.api.group.cluster.CorfuCluster;
import org.corfudb.universe.api.node.Node;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.group.cluster.AbstractCorfuCluster;
import org.corfudb.universe.group.cluster.CorfuClusterParams;
import org.corfudb.universe.logging.LoggingParams;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.node.server.CorfuServerParams;
import org.corfudb.universe.node.server.vm.VmCorfuServer;
import org.corfudb.universe.node.stress.vm.VmStress;
import org.corfudb.universe.universe.vm.VmManager;
import org.corfudb.universe.util.IpAddress;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provides VM implementation of a {@link CorfuCluster}.
 */
@Slf4j
public class VmCorfuCluster extends AbstractCorfuCluster<VmParams<CorfuServerParams>> {

    private final ImmutableMap<VmName, VmManager> vms;

    private final VmUniverseParams vmUniverseParams;

    @Builder
    protected VmCorfuCluster(CorfuClusterParams<VmParams<CorfuServerParams>> corfuClusterParams,
                             UniverseParams universeParams, ImmutableMap<VmName, VmManager> vms,
                             @NonNull LoggingParams loggingParams, VmUniverseParams vmUniverseParams) {
        super(corfuClusterParams, universeParams, loggingParams);
        this.vms = vms;
        this.vmUniverseParams = vmUniverseParams;

        init();
    }

    /**
     * Deploys a Corfu server node according to the provided parameter.
     *
     * @return an instance of {@link Node}
     */
    @Override
    protected CorfuServer buildServer(VmParams<CorfuServerParams> deploymentParams) {
        log.info("Deploy corfu server: {}", deploymentParams);
        VmManager vmManager = vms.get(deploymentParams.getVmName());

        RemoteOperationHelper commandHelper = RemoteOperationHelper.builder()
                .ipAddress(vmManager.getIpAddress())
                .credentials(vmUniverseParams.getCredentials().getVmCredentials())
                .build();

        VmStress stress = VmStress.builder()
                .params(deploymentParams.getApplicationParams())
                .vmManager(vmManager)
                .commandHelper(commandHelper)
                .build();

        return VmCorfuServer.builder()
                .universeParams(universeParams)
                .vmManager(vmManager)
                .stress(stress)
                .remoteOperationHelper(commandHelper)
                .loggingParams(loggingParams)
                .build();
    }

    @Override
    protected ImmutableSortedSet<String> getClusterLayoutServers() {
        return ImmutableSortedSet.copyOf(buildLayout().getLayoutServers());
    }

    @Override
    public void bootstrap() {
        Layout layout = buildLayout();
        log.info("Bootstrap corfu cluster. Cluster: {}. layout: {}", params.getName(), layout.asJSONString());

        BootstrapUtil.bootstrap(layout, params.getBootStrapRetries(), params.getRetryDuration());
    }

    /**
     * Build a layout from parameters
     *
     * @return an instance of {@link Layout} that is built from the existing parameters.
     */
    private Layout buildLayout() {
        long epoch = 0;
        UUID clusterId = UUID.randomUUID();

        List<String> servers = params.getNodesParams()
                .stream()
                .map(vmParams -> {
                    IpAddress ipAddress = vms.get(vmParams.getVmName()).getIpAddress();
                    Integer port = vmParams.getApplicationParams().getCommonParams().getPorts().iterator().next();
                    return ipAddress + ":" + port;
                })
                .collect(Collectors.toList());

        Layout.LayoutSegment segment = new Layout.LayoutSegment(
                Layout.ReplicationMode.CHAIN_REPLICATION,
                0L,
                -1L,
                Collections.singletonList(new Layout.LayoutStripe(servers))
        );
        return new Layout(servers, servers, Collections.singletonList(segment), epoch, clusterId);
    }


}
