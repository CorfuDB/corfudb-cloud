package org.corfudb.test.docker.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.failure.NodeFailure;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCorfuLongevityCluster;
import org.corfudb.universe.infrastructure.docker.universe.node.server.DockerCorfuServer.DockerCorfuLongevityApp;
import org.corfudb.universe.infrastructure.docker.workflow.DockerUniverseWorkflow;
import org.corfudb.universe.scenario.fixture.UniverseFixture;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Tag(TestGroups.LONGEVITY_DOCKER)
public class LongevityTest extends AbstractCorfuUniverseTest {

    private final Duration testDuration = Duration.ofHours(1);

    @Test
    public void test() {
        Consumer<UniverseFixture> setup = fixture -> {
            fixture.getLongevityAppCommonParams()
                    .enabled(true)
                    .universeDirectory(Paths.get("build"));

            fixture.getLongevityApp().timeAmount((int) testDuration.toMinutes());
        };

        testRunner.executeDockerTest(setup, wf -> {
            DockerCorfuLongevityCluster longevityCluster = runLongevity(wf);

            executeFailures(wf);

            waitForLongevityFinished(longevityCluster);
        });
    }

    private void executeFailures(DockerUniverseWorkflow wf) throws Exception {
        long start = System.currentTimeMillis();

        CorfuCluster<DeploymentParams<CorfuServerParams>, CorfuApplicationServer> corfuCluster = wf.getUniverse()
                .getGroup(ClusterType.CORFU);
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        while (true) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - start);

            if (duration.toMinutes() + 5 > testDuration.toMinutes()) {
                break;
            }

            int randomServerIndex = new Random().nextInt(3);
            NodeFailure oneNodeFailure = new NodeFailure(corfuClient, corfuCluster.getServerByIndex(randomServerIndex));
            oneNodeFailure.failure((client, server) -> {

            });
        }
    }

    private DockerCorfuLongevityCluster runLongevity(DockerUniverseWorkflow wf) {
        DockerCorfuLongevityCluster longevityCluster = wf
                .getUniverse()
                .getGroup(ClusterType.CORFU_LONGEVITY_APP);

        longevityCluster.nodes().forEach((name, app) -> {
            log.info("Application endpoint: {}, app params: {}", app.getEndpoint(), app.getParams());
        });
        return longevityCluster;
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
