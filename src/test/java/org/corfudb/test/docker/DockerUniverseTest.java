package org.corfudb.test.docker;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.collections.CorfuTable;
import org.corfudb.test.TestGroups;
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.UniverseConfigurator;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.corfudb.universe.scenario.fixture.Fixtures.TestFixtureConst.DEFAULT_STREAM_NAME;
import static org.corfudb.universe.scenario.fixture.Fixtures.TestFixtureConst.DEFAULT_TABLE_ITER;

@Slf4j
@Tag(TestGroups.DOCKER)
public class DockerUniverseTest {

    private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();
    private final UniverseManager universeManager = configurator.dockerUniverseManager;

    @Test
    public void test1() {
        universeManager.workflow(wf -> {
            wf.setupDocker(configurator.dockerSetup);
            wf.deploy();
            testingLogic(wf);
        });
    }

    private void testingLogic(UniverseWorkflow<Fixture<UniverseParams>> wf) {
        CorfuCluster corfuCluster = wf.getUniverse()
                .getGroup(wf.getFixture().data().getGroupParamByIndex(0).getName());

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuTable<String, String> table = corfuClient
                .createDefaultCorfuTable(DEFAULT_STREAM_NAME);

        for (int i = 0; i < DEFAULT_TABLE_ITER; i++) {
            table.put(String.valueOf(i), String.valueOf(i));
        }
    }
}
