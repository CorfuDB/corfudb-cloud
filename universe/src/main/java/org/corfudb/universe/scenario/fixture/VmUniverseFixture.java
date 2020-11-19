package org.corfudb.universe.scenario.fixture;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.common.LoggingParams.LoggingParamsBuilder;
import org.corfudb.universe.api.deployment.vm.VmParams;
import org.corfudb.universe.api.deployment.vm.VmParams.VmCredentialsParams;
import org.corfudb.universe.api.deployment.vm.VmParams.VsphereParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.node.CommonNodeParams;
import org.corfudb.universe.api.universe.node.CommonNodeParams.CommonNodeParamsBuilder;
import org.corfudb.universe.infrastructure.vm.universe.VmConfigPropertiesLoader;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuClusterParams;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuClusterParams.CorfuClusterParamsBuilder;
import org.corfudb.universe.universe.node.client.ClientParams;
import org.corfudb.universe.universe.node.client.ClientParams.ClientParamsBuilder;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams.CorfuServerParamsBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.corfudb.universe.api.universe.node.Node.NodeType.CORFU;
import static org.corfudb.universe.scenario.fixture.VmUniverseFixture.VmFixtureContext;

@Getter
public
class VmUniverseFixture implements Fixture<VmFixtureContext> {
    private static final IpAddress ANY_ADDRESS = IpAddress.builder().ip("0.0.0.0").build();
    private static final String DEFAULT_VM_PREFIX = "corfu-vm-";

    private final UniverseParams.UniverseParamsBuilder universe = UniverseParams.builder();

    private final VsphereParams.VsphereParamsBuilder vsphere = VsphereParams.builder();

    private final CorfuClusterParamsBuilder<VmParams<CorfuServerParams>> cluster = CorfuClusterParams
            .builder();

    private final CorfuServerParamsBuilder server = CorfuServerParams.builder();
    private final CommonNodeParamsBuilder commonServerParams = CommonNodeParams.builder()
            .nodeType(CORFU)
            .enabled(true);

    private final ClientParamsBuilder client = ClientParams.builder();

    @Setter
    private String vmPrefix = DEFAULT_VM_PREFIX;

    private Optional<VmFixtureContext> data = Optional.empty();

    private final FixtureUtil.FixtureUtilBuilder fixtureUtilBuilder = FixtureUtil.builder();

    private final Properties vmProperties = VmConfigPropertiesLoader
            .loadVmProperties()
            .get();

    private final LoggingParamsBuilder logging = LoggingParams.builder()
            .enabled(false);

    public VmUniverseFixture() {
        Properties credentials = VmConfigPropertiesLoader
                .loadVmCredentialsProperties()
                .orElse(new Properties());

        VmParams.Credentials vsphereCred = VmParams.Credentials.builder()
                .username(credentials.getProperty("vsphere.username"))
                .password(credentials.getProperty("vsphere.password"))
                .build();

        VmParams.Credentials vmCred = VmParams.Credentials.builder()
                .username(credentials.getProperty("vm.username"))
                .password(credentials.getProperty("vm.password"))
                .build();

        VmCredentialsParams credentialParams = VmCredentialsParams.builder()
                .vsphereCredentials(vsphereCred)
                .vmCredentials(vmCred)
                .build();

        List<String> vsphereHost = Arrays
                .stream(vmProperties.getProperty("vsphere.host").split(","))
                .collect(Collectors.toList());

        vsphere
                .vsphereUrl(vmProperties.getProperty("vsphere.url"))
                .vsphereHost(vsphereHost)
                .networkName(vmProperties.getProperty("vm.network"))
                .templateVmName(vmProperties.getProperty("vm.template", "debian-buster-thin-provisioned"))
                .credentials(credentialParams);
    }

    @Override
    public VmFixtureContext data() {
        if (data.isPresent()) {
            return data.get();
        }

        vmPrefix = vmProperties.getProperty("vm.prefix", vmPrefix);

        CorfuClusterParams<VmParams<CorfuServerParams>> clusterParams = cluster.build();

        CommonNodeParams commonParams = CommonNodeParams.builder()
                .nodeType(CORFU)
                .clusterName(clusterParams.getName())
                .build();

        server.commonParams(commonParams);

        setupVsphere(clusterParams);

        VsphereParams vsphereParams = vsphere.build();

        FixtureUtil fixtureUtil = fixtureUtilBuilder.build();

        commonServerParams.clusterName(clusterParams.getName());

        ImmutableList<VmParams<CorfuServerParams>> serversParams = fixtureUtil.buildVmServers(
                clusterParams, server, vmPrefix, vsphereParams, commonServerParams
        );

        serversParams.forEach(clusterParams::add);

        UniverseParams universeParams = universe.build();

        universeParams.add(clusterParams);

        VmFixtureContext ctx = VmFixtureContext.builder()
                .vsphereParams(vsphereParams)
                .universeParams(universeParams)
                .build();

        data = Optional.of(ctx);

        return ctx;
    }

    private void setupVsphere(CorfuClusterParams<VmParams<CorfuServerParams>> clusterParams) {
        ConcurrentMap<VmParams.VmName, IpAddress> vmIpAddresses = new ConcurrentHashMap<>();
        for (int i = 0; i < clusterParams.getNumNodes(); i++) {
            VmParams.VmName vmName = VmParams.VmName.builder()
                    .name(vmPrefix + (i + 1))
                    .index(i)
                    .build();
            vmIpAddresses.put(vmName, ANY_ADDRESS);
        }
        vsphere.vmIpAddresses(vmIpAddresses);
    }

    @Builder
    @Getter
    public static class VmFixtureContext {
        @NonNull
        private final UniverseParams universeParams;

        @NonNull
        private final VsphereParams vsphereParams;
    }
}
