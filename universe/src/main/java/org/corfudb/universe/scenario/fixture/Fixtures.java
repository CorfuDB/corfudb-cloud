package org.corfudb.universe.scenario.fixture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams.DockerContainerParamsBuilder;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams.PortBinding;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams.VolumeBinding;
import org.corfudb.universe.api.deployment.vm.VmParams;
import org.corfudb.universe.api.deployment.vm.VmParams.Credentials;
import org.corfudb.universe.api.deployment.vm.VmParams.VmCredentialsParams;
import org.corfudb.universe.api.deployment.vm.VmParams.VmName;
import org.corfudb.universe.api.deployment.vm.VmParams.VsphereParams;
import org.corfudb.universe.api.deployment.vm.VmParams.VsphereParams.VsphereParamsBuilder;
import org.corfudb.universe.api.group.Group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.group.Group.GroupParams.GenericGroupParams.GenericGroupParamsBuilder;
import org.corfudb.universe.api.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.node.Node;
import org.corfudb.universe.api.node.Node.NodeParams.CommonNodeParams;
import org.corfudb.universe.api.node.Node.NodeType;
import org.corfudb.universe.api.node.NodeException;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.UniverseParams.UniverseParamsBuilder;
import org.corfudb.universe.group.cluster.CorfuClusterParams;
import org.corfudb.universe.group.cluster.CorfuClusterParams.CorfuClusterParamsBuilder;
import org.corfudb.universe.logging.LoggingParams;
import org.corfudb.universe.logging.LoggingParams.LoggingParamsBuilder;
import org.corfudb.universe.node.client.ClientParams;
import org.corfudb.universe.node.client.ClientParams.ClientParamsBuilder;
import org.corfudb.universe.node.server.CorfuServerParams;
import org.corfudb.universe.node.server.CorfuServerParams.CorfuServerParamsBuilder;
import org.corfudb.universe.node.server.SupportServerParams;
import org.corfudb.universe.node.server.SupportServerParams.SupportServerParamsBuilder;
import org.corfudb.universe.scenario.fixture.FixtureUtil.FixtureUtilBuilder;
import org.corfudb.universe.universe.vm.VmConfigPropertiesLoader;
import org.corfudb.universe.util.IpAddress;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Fixture factory provides predefined fixtures
 */
public interface Fixtures {

    IpAddress ANY_ADDRESS = IpAddress.builder().ip("0.0.0.0").build();

    /**
     * Common constants used for test
     */
    class TestFixtureConst {

        // Default name of the CorfuTable created by CorfuClient
        public static final String DEFAULT_STREAM_NAME = "stream";

        // Default number of values written into CorfuTable
        public static final int DEFAULT_TABLE_ITER = 100;

        // Default number of times to poll layout
        public static final int DEFAULT_WAIT_POLL_ITER = 300;

        // Default time to wait before next layout poll: 1 second
        public static final Duration DEFAULT_WAIT_TIME = Duration.ofSeconds(1);

        private TestFixtureConst() {
            // prevent instantiation of this class
        }
    }

    @Getter
    class UniverseFixture implements Fixture<UniverseParams> {

        private final UniverseParamsBuilder universe = UniverseParams.builder();

        private final CorfuClusterParamsBuilder<DockerContainerParams<CorfuServerParams>> cluster = CorfuClusterParams
                .builder();

        private final CorfuServerParamsBuilder server = CorfuServerParams.builder();

        private final DockerContainerParamsBuilder<CorfuServerParams> corfuServerContainer = DockerContainerParams
                .builder();

        private final SupportServerParamsBuilder supportServer = SupportServerParams.builder();

        private final GenericGroupParamsBuilder<SupportServerParams, DockerContainerParams<SupportServerParams>>
                monitoringCluster = GenericGroupParams
                .<SupportServerParams, DockerContainerParams<SupportServerParams>>builder()
                .nodeNamePrefix("support")
                .type(ClusterType.SUPPORT_CLUSTER)
                .numNodes(1);

        private final ClientParamsBuilder client = ClientParams.builder();

        private final FixtureUtilBuilder fixtureUtilBuilder = FixtureUtil.builder();

        private final LoggingParamsBuilder logging = LoggingParams.builder()
                .enabled(false);

        private Optional<UniverseParams> data = Optional.empty();

        public UniverseParams data() {
            if (data.isPresent()) {
                return data.get();
            }

            UniverseParams universeParams = universe.build();
            CorfuClusterParams<DockerContainerParams<CorfuServerParams>> clusterParams = cluster.build();

            FixtureUtil fixtureUtil = fixtureUtilBuilder.build();

            List<DockerContainerParams<CorfuServerParams>> serversParams = fixtureUtil.buildServers(
                    clusterParams, server, universeParams
            );

            serversParams.forEach(clusterParams::add);
            universeParams.add(clusterParams);

            setupMonitoring(universeParams);

            data = Optional.of(universeParams);
            return universeParams;
        }

        private void setupMonitoring(UniverseParams universeParams) {
            GenericGroupParams<SupportServerParams, DockerContainerParams<SupportServerParams>>
                    monitoringClusterParams = monitoringCluster.build();

            CommonNodeParams commonParams = CommonNodeParams.builder()
                    .clusterName(monitoringClusterParams.getName())
                    .nodeType(NodeType.METRICS_SERVER)
                    .nodeNamePrefix("support")
                    .ports(ImmutableSet.of(9090))
                    .build();

            SupportServerParams monitoringServerParams = supportServer
                    .commonParams(commonParams)
                    .build();

            VolumeBinding volume;
            try {
                volume = VolumeBinding.builder()
                        .containerPath(monitoringServerParams.getPrometheusConfigPath())
                        .hostPath(File.createTempFile("prometheus", ".yml").toPath())
                        .build();
            } catch (IOException e) {
                throw new NodeException("Can't deploy docker support server. Can't create a tmp directory");
            }

            List<PortBinding> ports = commonParams.getPorts().stream()
                    .map(PortBinding::new)
                    .collect(Collectors.toList());

            DockerContainerParams<SupportServerParams> containerParams = DockerContainerParams
                    .<SupportServerParams>builder()
                    .image("prom/prometheus")
                    .networkName(universeParams.getNetworkName())
                    .ports(ports)
                    .volume(volume)
                    .applicationParams(monitoringServerParams)
                    .build();

            if (monitoringServerParams.isEnabled()) {
                monitoringClusterParams.add(containerParams);
                universeParams.add(monitoringClusterParams);
            }
        }
    }

    @Getter
    class VmUniverseFixture implements Fixture<VmFixtureContext> {
        private static final String DEFAULT_VM_PREFIX = "corfu-vm-";

        private final UniverseParamsBuilder universe = UniverseParams.builder();

        private final VsphereParamsBuilder vsphere = VsphereParams.builder();

        private final CorfuClusterParamsBuilder<VmParams<CorfuServerParams>> cluster = CorfuClusterParams
                .builder();

        private final CorfuServerParamsBuilder server = CorfuServerParams.builder();

        private final ClientParamsBuilder client = ClientParams.builder();

        @Setter
        private String vmPrefix = DEFAULT_VM_PREFIX;

        private Optional<VmFixtureContext> data = Optional.empty();

        private final FixtureUtilBuilder fixtureUtilBuilder = FixtureUtil.builder();

        private final Properties vmProperties = VmConfigPropertiesLoader
                .loadVmProperties()
                .get();

        private final LoggingParamsBuilder logging = LoggingParams.builder()
                .enabled(false);

        public VmUniverseFixture() {
            Properties credentials = VmConfigPropertiesLoader
                    .loadVmCredentialsProperties()
                    .orElse(new Properties());

            Credentials vsphereCred = Credentials.builder()
                    .username(credentials.getProperty("vsphere.username"))
                    .password(credentials.getProperty("vsphere.password"))
                    .build();

            Credentials vmCred = Credentials.builder()
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
                    .nodeNamePrefix("corfu")
                    .nodeType(Node.NodeType.CORFU_SERVER)
                    .clusterName(clusterParams.getName())
                    .build();

            server.commonParams(commonParams);

            setupVsphere(clusterParams);

            VsphereParams vsphereParams = vsphere.build();

            FixtureUtil fixtureUtil = fixtureUtilBuilder.build();
            ImmutableList<VmParams<CorfuServerParams>> serversParams = fixtureUtil.buildVmServers(
                    clusterParams, server, vmPrefix, vsphereParams
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
            ConcurrentMap<VmName, IpAddress> vmIpAddresses = new ConcurrentHashMap<>();
            for (int i = 0; i < clusterParams.getNumNodes(); i++) {
                VmName vmName = VmName.builder()
                        .name(vmPrefix + (i + 1))
                        .index(i)
                        .build();
                vmIpAddresses.put(vmName, ANY_ADDRESS);
            }
            vsphere.vmIpAddresses(vmIpAddresses);
        }
    }

    @Builder
    @Getter
    class VmFixtureContext {
        @NonNull
        private final UniverseParams universeParams;

        @NonNull
        private final VsphereParams vsphereParams;
    }
}
