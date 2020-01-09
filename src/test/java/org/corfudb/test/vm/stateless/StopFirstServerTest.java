package org.corfudb.test.vm.stateless;

import static com.vmware.corfudb.universe.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.junit.jupiter.api.Assertions.fail;

import com.vmware.corfudb.universe.UniverseConfigurator;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.TestGroups;
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

@Slf4j
@Tag(TestGroups.STATELESS)
public class StopFirstServerTest {

    private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();
    private final UniverseManager universeManager = configurator.universeManager;

    @Test
    public void test() {

        universeManager.workflow(wf -> {
            wf.setupVm(configurator.vmSetup);
            wf.deploy();
            try {
                stopFirstNode(wf);
            } catch (Exception e) {
                fail("Failed", e);
            }
        });
    }

    private void stopFirstNode(UniverseWorkflow<Fixture<UniverseParams>> wf) throws Exception {
        CorfuCluster corfuCluster = wf.getUniverse()
                .getGroup(wf.getFixture().data().getGroupParamByIndex(0).getName());

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Should stop one node and then restart
        CorfuServer server0 = corfuCluster.getFirstServer();

        // Stop one node and wait for layout's unresponsive servers to change
        server0.stop(Duration.ofSeconds(10));
        waitForUnresponsiveServersChange(size -> size == 1, corfuClient);
    }
}
