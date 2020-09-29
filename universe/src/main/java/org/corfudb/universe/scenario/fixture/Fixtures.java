package org.corfudb.universe.scenario.fixture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.common.LoggingParams.LoggingParamsBuilder;
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
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.UniverseParams.UniverseParamsBuilder;
import org.corfudb.universe.api.universe.group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.universe.group.GroupParams.GenericGroupParams.GenericGroupParamsBuilder;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.CommonNodeParams;
import org.corfudb.universe.api.universe.node.CommonNodeParams.CommonNodeParamsBuilder;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.api.universe.node.Node.NodeType;
import org.corfudb.universe.api.universe.node.NodeException;
import org.corfudb.universe.infrastructure.vm.universe.VmConfigPropertiesLoader;
import org.corfudb.universe.scenario.fixture.FixtureUtil.FixtureUtilBuilder;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuClusterParams;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuClusterParams.CorfuClusterParamsBuilder;
import org.corfudb.universe.universe.node.client.ClientParams;
import org.corfudb.universe.universe.node.client.ClientParams.ClientParamsBuilder;
import org.corfudb.universe.universe.node.server.cassandra.CassandraServerParams;
import org.corfudb.universe.universe.node.server.cassandra.CassandraServerParams.CassandraServerParamsBuilder;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams.CorfuServerParamsBuilder;
import org.corfudb.universe.universe.node.server.mangle.MangleServerParams;
import org.corfudb.universe.universe.node.server.mangle.MangleServerParams.MangleServerParamsBuilder;
import org.corfudb.universe.universe.node.server.prometheus.PromServerParams;
import org.corfudb.universe.universe.node.server.prometheus.PromServerParams.PromServerParamsBuilder;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
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

    /**
     * Docker and Process fixture
     */
    @Getter
    class UniverseFixture implements Fixture<UniverseParams> {

        private final UniverseParamsBuilder universe = UniverseParams.builder();

        private final CorfuClusterParamsBuilder<DockerContainerParams<CorfuServerParams>> cluster = CorfuClusterParams
                .builder();

        private final CorfuServerParamsBuilder server = CorfuServerParams.builder();

        private final DockerContainerParamsBuilder<CorfuServerParams> corfuServerContainer = DockerContainerParams
                .builder();

        private final PromServerParamsBuilder prometheusServer = PromServerParams.builder();
        private final GenericGroupParamsBuilder<PromServerParams, DockerContainerParams<PromServerParams>>
                prometheusCluster = GenericGroupParams
                .<PromServerParams, DockerContainerParams<PromServerParams>>builder()
                .type(ClusterType.PROM)
                .numNodes(1);

        private final CassandraServerParamsBuilder cassandraServer = CassandraServerParams.builder();
        private final CommonNodeParamsBuilder cassandraCommonParams = CommonNodeParams.builder()
                .nodeType(NodeType.CASSANDRA)
                .ports(ImmutableSet.of(9042))
                .enabled(false);

        private final GenericGroupParamsBuilder<CassandraServerParams, DockerContainerParams<CassandraServerParams>>
                cassandraCluster = GenericGroupParams
                .<CassandraServerParams, DockerContainerParams<CassandraServerParams>>builder()
                .type(ClusterType.CASSANDRA)
                .numNodes(1);


        private final MangleServerParamsBuilder mangleServer = MangleServerParams.builder();
        private final CommonNodeParamsBuilder mangleCommonParams = CommonNodeParams.builder()
                .nodeType(NodeType.MANGLE)
                .ports(ImmutableSet.of(8080, 8443))
                .enabled(false);
        private final GenericGroupParamsBuilder<MangleServerParams, DockerContainerParams<MangleServerParams>>
                mangleCluster = GenericGroupParams
                .<MangleServerParams, DockerContainerParams<MangleServerParams>>builder()
                .type(ClusterType.MANGLE)
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

            setupPrometheus(universeParams);
            String cassandraNode = setupCassandra(universeParams);
            setupMangle(universeParams, cassandraNode);

            data = Optional.of(universeParams);
            return universeParams;
        }

        private void setupMangle(UniverseParams universeParams, String cassandraNode) {
            GenericGroupParams<MangleServerParams, DockerContainerParams<MangleServerParams>>
                    clusterParams = mangleCluster.build();

            CommonNodeParams commonParams = mangleCommonParams
                    .clusterName(clusterParams.getName())
                    .build();

            MangleServerParams serverParams = mangleServer
                    .commonParams(commonParams)
                    .build();

            List<PortBinding> ports = commonParams.getPorts().stream()
                    .map(PortBinding::new)
                    .collect(Collectors.toList());

            List<String> envs = new ArrayList<>();
            envs.add("DB_OPTIONS=-DcassandraContactPoints=" + cassandraNode + " -DcassandraSslEnabled=true");
            envs.add("CLUSTER_OPTIONS=-DclusterValidationToken=mangle -DpublicAddress=127.0.0.1");

            DockerContainerParams<MangleServerParams> containerParams = DockerContainerParams
                    .<MangleServerParams>builder()
                    .image("mangleuser/mangle")
                    .imageVersion("2.0")
                    .envs(envs)
                    .networkName(universeParams.getNetworkName())
                    .ports(ports)
                    .applicationParams(serverParams)
                    .build();

            if (commonParams.isEnabled()) {
                clusterParams.add(containerParams);
                universeParams.add(clusterParams);
            }
        }

        private String setupCassandra(UniverseParams universeParams) {
            GenericGroupParams<CassandraServerParams, DockerContainerParams<CassandraServerParams>>
                    clusterParams = cassandraCluster.build();

            CommonNodeParams commonParams = cassandraCommonParams
                    .clusterName(clusterParams.getName())
                    .build();

            CassandraServerParams cassandraServerParams = cassandraServer
                    .commonParams(commonParams)
                    .build();

            List<PortBinding> ports = commonParams.getPorts().stream()
                    .map(PortBinding::new)
                    .collect(Collectors.toList());

            List<String> envs = new ArrayList<>();
            envs.add("CASSANDRA_CLUSTER_NAME=cassandracluster");
            envs.add("CASSANDRA_DC=DC1");
            envs.add("CASSANDRA_RACK=rack1");
            envs.add("CASSANDRA_ENDPOINT_SNITCH=GossipingPropertyFileSnitch");

            DockerContainerParams<CassandraServerParams> containerParams = DockerContainerParams
                    .<CassandraServerParams>builder()
                    .image("cassandra")
                    .imageVersion("3.11")
                    .envs(envs)
                    .networkName(universeParams.getNetworkName())
                    .ports(ports)
                    .applicationParams(cassandraServerParams)
                    .build();

            if (commonParams.isEnabled()) {
                clusterParams.add(containerParams);
                universeParams.add(clusterParams);
            }

            return commonParams.getName();
        }

        private void setupPrometheus(UniverseParams universeParams) {
            GenericGroupParams<PromServerParams, DockerContainerParams<PromServerParams>>
                    monitoringClusterParams = prometheusCluster.build();

            CommonNodeParams commonParams = CommonNodeParams.builder()
                    .clusterName(monitoringClusterParams.getName())
                    .nodeType(NodeType.PROMETHEUS)
                    .ports(ImmutableSet.of(9090))
                    .enabled(false)
                    .build();

            PromServerParams promServerParams = prometheusServer
                    .commonParams(commonParams)
                    .build();

            VolumeBinding volume;
            try {
                volume = VolumeBinding.builder()
                        .containerPath(promServerParams.getPrometheusConfigPath())
                        .hostPath(File.createTempFile("prometheus", ".yml").toPath())
                        .build();
            } catch (IOException e) {
                throw new NodeException("Can't deploy docker support server. Can't create a tmp directory");
            }

            List<PortBinding> ports = commonParams.getPorts().stream()
                    .map(PortBinding::new)
                    .collect(Collectors.toList());

            DockerContainerParams<PromServerParams> containerParams = DockerContainerParams
                    .<PromServerParams>builder()
                    .image("prom/prometheus")
                    .networkName(universeParams.getNetworkName())
                    .ports(ports)
                    .volume(volume)
                    .applicationParams(promServerParams)
                    .build();

            if (commonParams.isEnabled()) {
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
                    .nodeType(Node.NodeType.CORFU)
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
