package org.corfudb.test.vm.stateful.ufo.restart;

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

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class RestartServiceOnThreeNodesOneHundredTimesTest extends AbstractCorfuUniverseTest {
    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after service restarted on all three nodes 100 times
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Verify Cluster is stable after deployment
     * 3) Create a table in corfu
     * 4) Add 100 Entries into table
     * 5) Verification by number of rows count i.e (Total rows: 100) and verify table content
     * 6) Restart (stop/start) the "corfu" service on all cluster nodes 100 times
     * 7) Wait for nodes to get responsive
     * 8) Add more 100 Entries into table and verify count and data of table
     * 9) Update the table entries from 60 to 90
     * 10) Verification by number of rows count i.e (Total rows: 200) and verify table content
     * 11) clear the contents of the table
     */
    @Test
    public void test() {
        testRunner.executeStatefulVmTest(this::verifyRestartService);
    }

    private void verifyRestartService(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf)
            throws InterruptedException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);


        // Define table name
        String tableName = getClass().getSimpleName();
        SpecHelper helper = new SpecHelper(runtime, tableName);//Check CLUSTER STATUS
        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);


        final int count = 100;
        List<Uuid> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
        helper.transactional((utils, txn) -> utils.generateData(0, count, uuids, events, txn, false));

        // Add data in table (100 entries)
        log.info("**** Add 1st set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(0, count, uuids, events, txn, false));
        // Verify table row count (should be 100)
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count));
        log.info("**** First Insertion Verification:: Verify Table Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 0, count, false));
        log.info("**** First Insertion Verified... ****");

        // Loop for 100 times restart service on all nodes serially
        for (int loopCount = 1; loopCount <= 100; loopCount++) {
            log.info("**** In Loop :: " + loopCount + " ****");

            // Loop for all 3 nodes restart service
            for (int nodeIndex = 0; nodeIndex < 3; nodeIndex++) {
                CorfuApplicationServer server = corfuCluster.getServerByIndex(nodeIndex);
                // First it'll stop and then start service
                log.info("**** Restarting server" + nodeIndex + " ****");
                server.restart();
                waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
            }
            waitUninterruptibly(Duration.ofSeconds(30));
            log.info("**** Wait for cluster status STABLE :: after restarting service on all servers ****");
            waitForClusterStatusStable(corfuClient);
        }
        log.info("**** Wait for cluster status STABLE :: after 100 times restart ****");
        waitForClusterStatusStable(corfuClient);

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

        // Verify all data in table
        log.info("**** Verify Table Data (200 rows) one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 0, 50, false));
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 151, count * 2, false));
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 51, 150, true));

        // Clear table data and verify

        log.info("**** Clear all data from table ****");
        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
