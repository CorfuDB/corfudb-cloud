package org.corfudb.test.docker.infrastructure;

//import com.vmware.mangle.invoker.ApiClient;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCorfuLongevityCluster;
import org.corfudb.universe.infrastructure.docker.universe.node.server.DockerCorfuServer.DockerCorfuLongevityApp;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

@Slf4j
@Tag(TestGroups.LONGEVITY_DOCKER)
public class LongevityTest extends AbstractCorfuUniverseTest {

    @Test
    public void test() {
        testRunner.executeDockerTest(wf -> {
            DockerCorfuLongevityCluster longevityCluster = wf
                    .getUniverse()
                    .getGroup(ClusterType.CORFU_LONGEVITY_APP);

            longevityCluster.nodes().forEach((name, app) -> {
                log.info("Application endpoint: {}, app params: {}", app.getEndpoint(), app.getParams());
            });

            while (true) {
                boolean completed = true;
                for (DockerCorfuLongevityApp node : longevityCluster.nodes().values()) {
                    if (!node.isRunning()) {
                        continue;
                    }

                    completed = false;
                    break;
                }

                if (!completed) {
                    TimeUnit.MINUTES.sleep(1);
                }
            }
        });
    }
}
