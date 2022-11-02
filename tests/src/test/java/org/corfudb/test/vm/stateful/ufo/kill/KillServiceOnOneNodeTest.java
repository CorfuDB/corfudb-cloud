package org.corfudb.test.vm.stateful.ufo.kill;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.ExampleSchemas.Uuid;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema;
import org.corfudb.test.spec.api.GenericSpec.SpecHelper;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class KillServiceOnOneNodeTest extends AbstractCorfuUniverseTest {
    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after kill service on one node
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Create a table in corfu
     * 3) Add 100 Entries into table and verify count and data of table
     * 4) Kill service on node0
     * 5) Verify cluster status (should be DEGRADED)
     * 6) Add 100 more Entries into table and verify count and data of table
     * 7) Update Records from 51 to 150 index and verify
     * 8) Start service on node0
     * 9) Verify cluster status (should be STABLE)
     * 10) Add 100 more Entries into table and verify count and data of table
     * 11) Clear the table and verify table contents are cleared
     */
    @Test
    public void test() {
        testRunner.executeStatefulVmTest(this::verifyKillService);
    }

    private void verifyKillService(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf) throws Exception {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);


        // Define table name
        String tableName = getClass().getSimpleName();
        SpecHelper helper = new SpecHelper(runtime, tableName);
        final int count = 100;
        List<Uuid> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
// Add data in table (100 entries)
        log.info("**** Add 1st set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(0, count, uuids, events, txn, false));
        // Verify table row count (should be 100)
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count));
        log.info("**** First Insertion Verification:: Verify Table Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 0, count, false));
        log.info("**** First Insertion Verified... ****");

        // Get first node of corfu cluster
        CorfuApplicationServer server = corfuCluster.getFirstServer();
        // kill corfu service and wait for layout's unresponsive servers to change
        server.kill();
        waitForUnresponsiveServersChange(size -> size == 1, corfuClient);
        // Verify cluster status is DEGRADED with one node down
        log.info("**** Verify cluster status is DEGRADED after service is killed on server0 ****");
        waitForClusterStatusDegraded(corfuClient);

        // Add 100 more entries in table
        log.info("**** Add 2nd set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(100, 200, uuids, events, txn, false));
        // Verify table row count (should be 200)
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, 200));
        log.info("**** Second Insertion Verification:: Verify Table Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 0, 200, false));
        log.info("**** Second Insertion Verified... ****");

        //Update table records from 51 to 150
        log.info("**** Update the records ****");
        helper.transactional((utils, txn) -> utils.generateData(51, 150, uuids, events, txn, true));
        // Verify table row count (should be 200)
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * 2));
        log.info("**** Record Updation Verification:: Verify Table Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 51, 150, true));
        log.info("**** Record Updation Verified ****");

        // start the corfu service and wait for cluster status to be STABLE
        log.info("**** Start service on node ****");
        server.start();
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
        log.info("**** After starting service, checking cluster status ****");
        waitForClusterStatusStable(corfuClient);

        // Add 100 more entries in table
        log.info("**** Add 3rd set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(200, 300, uuids, events, txn, false));
        // Verify table row count (should be 300)
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * 3));

        // Verify all data in table
        log.info("**** Third Insertion Verification: Verify Table Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, count * 2, count * 3, false));
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 151, count * 3, false));

        // Clear table data and verify
        log.info("**** Clear all data from table ****");

        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
