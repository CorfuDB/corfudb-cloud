package org.corfudb.test.docker.compatibility;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.spec.NodeDownAndLinkFailureSpec;
import org.corfudb.universe.api.universe.group.GroupParams;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCorfuCluster;
import org.corfudb.universe.scenario.fixture.UniverseFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

@Slf4j
@Tag(TestGroups.VERSION_COMPATIBILITY)
public class NodeDownAndLinkFailureTest extends AbstractCorfuUniverseTest {

    /**
     * Test cluster behavior after one node down and one link failure.
     * <p>
     *     1) Deploy and bootstrap a three nodes cluster with default cross version setup
     *     2) Stop one node
     *     3) Create a link failure between two nodes which results in a partial partition
     *     4) Restart the stopped node
     *     5) Verify layout, cluster status and data path
     *     6) Remove the link failure
     *     7) Verify layout, cluster status and data path again
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

            NodeDownAndLinkFailureSpec spec = new NodeDownAndLinkFailureSpec();
            spec.downAndLinkFailure(wf);
        });
    }
}
