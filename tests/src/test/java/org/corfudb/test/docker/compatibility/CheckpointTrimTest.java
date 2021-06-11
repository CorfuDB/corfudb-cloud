package org.corfudb.test.docker.compatibility;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.spec.CheckPointTrimSpec;
import org.corfudb.universe.api.universe.group.GroupParams;
import org.corfudb.universe.infrastructure.docker.universe.group.cluster.DockerCorfuCluster;
import org.corfudb.universe.scenario.fixture.UniverseFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

@Slf4j
@Tag(TestGroups.VERSION_COMPATIBILITY)
public class CheckpointTrimTest extends AbstractCorfuUniverseTest {

    /**
     * Test cluster behavior upon checkpoint trim
     * <p>
     * 1) Deploy and bootstrap a three nodes cluster with default cross version setup
     * 2) Place 3 entries into the map
     * 3) Insert a checkpoint
     * 4) Get a new view of the map
     * 5) Reading an entry from scratch should be ok
     * </p>
     */
    @Test
    public void checkpointTrimTest() {
        Consumer<UniverseFixture> setup = fixture -> {
            GroupParams.BootstrapParams bootstrapParams =
                    GroupParams.BootstrapParams.builder().enabled(false).build();
            fixture.getCluster().numNodes(1);
            fixture.getCluster().bootstrapParams(bootstrapParams);
        };

        testRunner.executeDockerTest(setup, wf -> {
            DockerCorfuCluster corfuCluster = getDefaultCrossVersionCluster(wf);
            corfuCluster.bootstrap(true);

            CheckPointTrimSpec spec = new CheckPointTrimSpec();
            spec.checkPointTrim(wf);
        });
    }
}
