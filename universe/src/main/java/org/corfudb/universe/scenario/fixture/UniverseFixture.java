package org.corfudb.universe.scenario.fixture;

import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams.DockerContainerParamsBuilder;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams.PortBinding;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.universe.group.GroupParams.GenericGroupParams.GenericGroupParamsBuilder;
import org.corfudb.universe.api.universe.group.cluster.Cluster;
import org.corfudb.universe.api.universe.node.CommonNodeParams;
import org.corfudb.universe.api.universe.node.CommonNodeParams.CommonNodeParamsBuilder;
import org.corfudb.universe.api.universe.node.Node;
import org.corfudb.universe.api.universe.node.NodeException;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuClusterParams;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuClusterParams.CorfuClusterParamsBuilder;
import org.corfudb.universe.universe.node.client.ClientParams;
import org.corfudb.universe.universe.node.server.cassandra.CassandraServerParams;
import org.corfudb.universe.universe.node.server.cassandra.CassandraServerParams.CassandraServerParamsBuilder;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams.CorfuServerParamsBuilder;
import org.corfudb.universe.universe.node.server.corfu.LongevityAppParams;
import org.corfudb.universe.universe.node.server.corfu.LongevityAppParams.LongevityAppParamsBuilder;
import org.corfudb.universe.universe.node.server.mangle.MangleServerParams;
import org.corfudb.universe.universe.node.server.mangle.MangleServerParams.MangleServerParamsBuilder;
import org.corfudb.universe.universe.node.server.prometheus.PromServerParams;
import org.corfudb.universe.universe.node.server.prometheus.PromServerParams.PromServerParamsBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.corfudb.universe.api.universe.node.Node.NodeType.CORFU;

/**
 * Docker and Process fixture
 */
@Getter
public class UniverseFixture implements Fixture<UniverseParams> {

    private final UniverseParams.UniverseParamsBuilder universe = UniverseParams.builder();

    private final CorfuClusterParamsBuilder<DockerContainerParams<CorfuServerParams>> cluster = CorfuClusterParams
            .builder();

    private final CorfuServerParamsBuilder server = CorfuServerParams.builder();

    private final CommonNodeParamsBuilder commonServerParams = CommonNodeParams.builder()
            .nodeType(CORFU)
            .enabled(true);

    private final DockerContainerParamsBuilder<CorfuServerParams> corfuServerContainer = DockerContainerParams
            .<CorfuServerParams>builder()
            .image(CorfuServerParams.DOCKER_IMAGE_NAME);

    private final PromServerParamsBuilder prometheusServer = PromServerParams.builder();
    private final GenericGroupParamsBuilder<PromServerParams, DockerContainerParams<PromServerParams>>
            prometheusCluster = GenericGroupParams
            .<PromServerParams, DockerContainerParams<PromServerParams>>builder()
            .type(Cluster.ClusterType.PROM)
            .numNodes(1);

    // Cassandra server
    private final CassandraServerParamsBuilder cassandraServer = CassandraServerParams.builder();
    private final CommonNodeParamsBuilder cassandraCommonParams = CommonNodeParams.builder()
            .nodeType(Node.NodeType.CASSANDRA)
            .ports(ImmutableSet.of(9042))
            .enabled(false);

    private final GenericGroupParamsBuilder<CassandraServerParams, DockerContainerParams<CassandraServerParams>>
            cassandraCluster = GenericGroupParams
            .<CassandraServerParams, DockerContainerParams<CassandraServerParams>>builder()
            .type(Cluster.ClusterType.CASSANDRA)
            .numNodes(1);


    // Mangle server
    private final MangleServerParamsBuilder mangleServer = MangleServerParams.builder();
    private final CommonNodeParamsBuilder mangleCommonParams = CommonNodeParams.builder()
            .nodeType(Node.NodeType.MANGLE)
            .ports(ImmutableSet.of(8080, 8443))
            .enabled(false);
    private final GenericGroupParamsBuilder<MangleServerParams, DockerContainerParams<MangleServerParams>>
            mangleCluster = GenericGroupParams
            .<MangleServerParams, DockerContainerParams<MangleServerParams>>builder()
            .type(Cluster.ClusterType.MANGLE)
            .numNodes(1);


    //Corfu Longevity App
    private final LongevityAppParamsBuilder longevityApp = LongevityAppParams.builder();
    private final CommonNodeParamsBuilder longevityAppCommonParams = CommonNodeParams.builder()
            .nodeType(Node.NodeType.CORFU_LONGEVITY_APP)
            .enabled(false);
    private final DockerContainerParamsBuilder<LongevityAppParams> longevityContainerParams =
            DockerContainerParams.builder();
    private final GenericGroupParamsBuilder<LongevityAppParams, DockerContainerParams<LongevityAppParams>>
            longevityCluster = GenericGroupParams
            .<LongevityAppParams, DockerContainerParams<LongevityAppParams>>builder()
            .type(Cluster.ClusterType.CORFU_LONGEVITY_APP)
            .numNodes(1);


    private final ClientParams.ClientParamsBuilder client = ClientParams.builder();

    private final FixtureUtil.FixtureUtilBuilder fixtureUtilBuilder = FixtureUtil.builder();

    private final LoggingParams.LoggingParamsBuilder logging = LoggingParams.builder()
            .enabled(false);

    private Optional<UniverseParams> data = Optional.empty();

    public UniverseParams data() {
        if (data.isPresent()) {
            return data.get();
        }

        UniverseParams universeParams = universe.build();
        setupCorfu(universeParams);
        setupLongevityApp(universeParams);

        setupPrometheus(universeParams);
        String cassandraNode = setupCassandra(universeParams);
        setupMangle(universeParams, cassandraNode);

        data = Optional.of(universeParams);
        return universeParams;
    }

    private void setupCorfu(UniverseParams universeParams) {
        CorfuClusterParams<DockerContainerParams<CorfuServerParams>> clusterParams = cluster.build();

        FixtureUtil fixtureUtil = fixtureUtilBuilder.build();

        corfuServerContainer
                .imageVersion(clusterParams.getServerVersion())
                .networkName(universeParams.getNetworkName());

        commonServerParams.clusterName(clusterParams.getName());

        List<DockerContainerParams<CorfuServerParams>> serversParams = fixtureUtil.buildServers(
                clusterParams, server, corfuServerContainer, commonServerParams
        );

        serversParams.forEach(corfuServer -> {
            if (corfuServer.getApplicationParams().getCommonParams().isEnabled()) {
                clusterParams.add(corfuServer);
            }
        });
        universeParams.add(clusterParams);
    }

    private void setupLongevityApp(UniverseParams universeParams) {
        GenericGroupParams<LongevityAppParams, DockerContainerParams<LongevityAppParams>> clusterParams =
                longevityCluster.build();

        CommonNodeParams commonParams = longevityAppCommonParams
                .clusterName(clusterParams.getName())
                .build();

        LongevityAppParams longevityParams = longevityApp
                .commonParams(commonParams)
                .build();

        DockerContainerParams<LongevityAppParams> containerParams = longevityContainerParams
                .image("corfudb-universe/generator")
                .networkName(universeParams.getNetworkName())
                .applicationParams(longevityParams)
                .build();

        if (commonParams.isEnabled()) {
            clusterParams.add(containerParams);
            universeParams.add(clusterParams);
        }
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
        envs.add("DB_OPTIONS=-DcassandraContactPoints=" + cassandraNode + " -DcassandraSslEnabled=false");
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
                .nodeType(Node.NodeType.PROMETHEUS)
                .ports(ImmutableSet.of(9090))
                .enabled(false)
                .build();

        PromServerParams promServerParams = prometheusServer
                .commonParams(commonParams)
                .build();

        DockerContainerParams.VolumeBinding volume;
        try {
            volume = DockerContainerParams.VolumeBinding.builder()
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
