package org.corfudb.test.docker.compatibility;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.spec.ClusterDetachRejoinTwoNodesSpec;
import org.corfudb.universe.api.universe.group.GroupParams;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCorfuCluster;
import org.corfudb.universe.scenario.fixture.UniverseFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

@Slf4j
@Tag(TestGroups.VERSION_COMPATIBILITY)
public class ClusterResizeTest extends AbstractCorfuUniverseTest {

    /**
     * Test cluster behavior after add/remove nodes
     * <p>
     *     1) Deploy and bootstrap a three nodes cluster
     *     2) Create a table in corfu
     *     3) Add 100 Entries into table and verify count and data of table
     *     4) Remove two nodes from cluster
     *     5) Verify Layout
     *     6) Add 100 more Entries into table and verify count and data of table
     *     7) Reattach the two detached nodes into cluster
     *     8) Verify Layout
     *     9) Update Records from 60 to 139 index and Verify
     *     10) Verify the table contents and updated data
     *     11) Clear the table and verify table contents are cleared
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

            ClusterDetachRejoinTwoNodesSpec spec = new ClusterDetachRejoinTwoNodesSpec();
            spec.clusterDetachRejoin(wf);
        });
    }
}
