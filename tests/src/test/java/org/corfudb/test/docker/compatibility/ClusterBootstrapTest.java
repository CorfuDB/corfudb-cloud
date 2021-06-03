package org.corfudb.test.docker.compatibility;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.universe.api.universe.group.GroupParams;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCorfuCluster;
import org.corfudb.universe.scenario.fixture.UniverseFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

@Slf4j
@Tag(TestGroups.VERSION_COMPATIBILITY)
public class ClusterBootstrapTest extends AbstractCorfuUniverseTest {

    /**
     * A simple test that bootstrap a 3 node corfu cluster with default cross version setup.
     */
    @Test
    public void clusterBootstrapTest() {
        Consumer<UniverseFixture> setup = fixture -> {
            GroupParams.BootstrapParams bootstrapParams =
                    GroupParams.BootstrapParams.builder().enabled(false).build();
            fixture.getCluster().numNodes(1);
            fixture.getCluster().bootstrapParams(bootstrapParams);
        };

        testRunner.executeDockerTest(setup, wf -> {
            DockerCorfuCluster corfuCluster = getDefaultCrossVersionCluster(wf);

            corfuCluster.bootstrap(true);
        });
    }
}
