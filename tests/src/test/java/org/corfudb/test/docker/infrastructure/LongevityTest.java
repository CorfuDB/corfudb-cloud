package org.corfudb.test.docker.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCorfuLongevityCluster;
import org.corfudb.universe.infrastructure.docker.universe.node.server.DockerCorfuServer.DockerCorfuLongevityApp;
import org.corfudb.universe.scenario.fixture.UniverseFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Tag(TestGroups.LONGEVITY_DOCKER)
public class LongevityTest extends AbstractCorfuUniverseTest {

    @Test
    public void test() {
        Consumer<UniverseFixture> setup = fixture -> {
            fixture.getLongevityAppCommonParams()
                    .enabled(true)
                    .universeDirectory(Paths.get("build"));

            fixture.getLongevityApp().timeAmount(5);
        };

        testRunner.executeDockerTest(setup, wf -> {
            DockerCorfuLongevityCluster longevityCluster = wf
                    .getUniverse()
                    .getGroup(ClusterType.CORFU_LONGEVITY_APP);

            longevityCluster.nodes().forEach((name, app) -> {
                log.info("Application endpoint: {}, app params: {}", app.getEndpoint(), app.getParams());
            });

            waitForLongevityFinished(longevityCluster);

            //Run Injection verification
        });
    }

    private void waitForLongevityFinished(DockerCorfuLongevityCluster longevityCluster) throws Exception {
        while (true) {
            DockerCorfuLongevityApp longevityNode = longevityCluster
                    .nodes().values().stream()
                    .findFirst()
                    .get();

            if (!longevityNode.isRunning()) {
                break;
            }

            TimeUnit.SECONDS.sleep(10);
        }
    }
}
