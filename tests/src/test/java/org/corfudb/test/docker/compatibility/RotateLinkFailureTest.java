package org.corfudb.test.docker.compatibility;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.spec.RotateLinkFailureSpec;
import org.corfudb.universe.api.universe.group.GroupParams;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCorfuCluster;
import org.corfudb.universe.scenario.fixture.UniverseFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

@Slf4j
@Tag(TestGroups.VERSION_COMPATIBILITY)
public class RotateLinkFailureTest extends AbstractCorfuUniverseTest {

    /**
     * Test cluster behavior when rotating link failure among nodes
     * <p>
     * 1) Deploy and bootstrap a three nodes cluster with default cross version setup
     * 2) Create a link failure between node0 and node1
     * 3) Create a link failure between node1 and node2 and heal previous link failure
     * 4) Create a link failure between node2 and node0 and heal previous link failure
     * 5) Reverse rotation direction, create a link failure between node1 and node2 and heal previous link failure
     * 6) Verify layout and data path after each rotation
     * 7) Recover cluster by removing all link failures
     * 8) Verify layout, cluster status and data path
     * </p>
     */
    @Test
    public void test() {
        Consumer<UniverseFixture> setup = fixture -> {
            GroupParams.BootstrapParams bootstrapParams =
                    GroupParams.BootstrapParams.builder().enabled(false).build();
            fixture.getCluster().numNodes(1);
            fixture.getCluster().bootstrapParams(bootstrapParams);
        };

        testRunner.executeDockerTest(setup, wf -> {
            DockerCorfuCluster corfuCluster = getDefaultCrossVersionCluster(wf);
            corfuCluster.bootstrap(true);

            RotateLinkFailureSpec spec = new RotateLinkFailureSpec();
            spec.rotateLinkFailure(wf);
        });
    }
}
