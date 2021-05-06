package org.corfudb.test.docker.compatibility;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.spec.NodeRebootSpec;
import org.corfudb.universe.api.universe.group.GroupParams;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCorfuCluster;
import org.corfudb.universe.scenario.fixture.UniverseFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

@Slf4j
@Tag(TestGroups.VERSION_COMPATIBILITY)
public class NodeRebootTest extends AbstractCorfuUniverseTest {

    /**
     * Test cluster behavior upon rebooting the nodes
     * <p>
     *     1) Deploy and bootstrap a three nodes cluster
     *     2) Create a table in corfu
     *     3) Add 100 Entries into table and verify count and data of table
     *     4) Reboot all the three nodes in the cluster without reset data
     *     5) Verify the data is still there
     * </p>
     */
    @Test
    public void nodeRebootTest() {
        Consumer<UniverseFixture> setup = fixture -> {
            GroupParams.BootstrapParams bootstrapParams =
                    GroupParams.BootstrapParams.builder().enabled(false).build();
            fixture.getCluster().numNodes(1);
            fixture.getCluster().bootstrapParams(bootstrapParams);
        };

        testRunner.executeDockerTest(setup, wf -> {
            DockerCorfuCluster corfuCluster = getDefaultCrossVersionCluster(wf);
            corfuCluster.bootstrap(true);

            NodeRebootSpec spec = new NodeRebootSpec();
            spec.nodeReboot(wf);
        });
    }
}
