package org.corfudb.universe.infrastructure.docker.universe.group.cluster;

import org.corfudb.common.util.ClassUtils;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.infrastructure.docker.universe.DockerUniverse;
import org.corfudb.universe.infrastructure.docker.universe.DockerUniverse.DockerClusterBuilder;

/**
 * Pluggable clusters into universe framework core
 */
public class ExternalDockerClusters {

    private final DockerClusterBuilder prometheus = (groupParams, dockerClient, universeParams, loggingParams) ->
            DockerCorfuCluster.builder()
                    .universeParams(universeParams)
                    .params(ClassUtils.cast(groupParams))
                    .loggingParams(loggingParams)
                    .docker(dockerClient)
                    .build();

    private final DockerClusterBuilder cassandra = (groupParams, dockerClient, universeParams, loggingParams) ->
            DockerCassandraCluster.builder()
                    .universeParams(universeParams)
                    .cassandraParams(ClassUtils.cast(groupParams))
                    .docker(dockerClient)
                    .loggingParams(loggingParams)
                    .build();

    private final DockerClusterBuilder mangle = (groupParams, dockerClient, universeParams, loggingParams) ->
            DockerMangleCluster.builder()
                    .universeParams(universeParams)
                    .docker(dockerClient)
                    .containerParams(ClassUtils.cast(groupParams))
                    .loggingParams(loggingParams)
                    .build();

    /**
     * Initia,ize external clusters
     */
    public void init() {
        DockerUniverse.CLUSTERS.add(ClusterType.PROM, prometheus);
        DockerUniverse.CLUSTERS.add(ClusterType.CASSANDRA, cassandra);
        DockerUniverse.CLUSTERS.add(ClusterType.MANGLE, mangle);
    }
}
