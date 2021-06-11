package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.spec.api.GenericSpec;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForLayoutChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
 * <p>
 * Test cluster behavior after kill service on one node
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Create a table in corfu
 * 3) Add 100 Entries into table and verify count and data of table
 * 4) Disconnect first server
 * 5) Verify cluster status (should be DEGRADED)
 * 6) Add 100 more Entries into table and verify count and data of table
 * 7) Update Records from 51 to 150 index and verify
 * 8) Reconnect first server
 * 9) Verify cluster status (should be STABLE)
 * 10) Add 100 more Entries into table and verify count and data of table
 * 11) Clear the table and verify table contents are cleared
 */
@Slf4j
public class DisconnectFirstServerSpec {

    /**
     * verifyDisconnectFirstServer
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void disconnectServer(
            U wf) throws Exception {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Define table name
        String tableName = getClass().getSimpleName();

        GenericSpec.SpecHelper helper = new GenericSpec.SpecHelper(runtime, tableName);

        final int count = 100;
        List<IdMessage> uuids = new ArrayList<>();
        List<EventInfo> events = new ArrayList<>();

        // Add data in table (100 entries)
        log.info("**** Add 1st set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(0, count, uuids, events, txn, false));
        helper.transactional((utils, txn) -> {
            // Verify table row count (should be 100)
            utils.verifyTableRowCount(txn, count);
            log.info("**** First Insertion Verification:: Verify Table Data one by one ****");
            utils.verifyTableData(txn, 0, count, false);
            log.info("**** First Insertion Verified... ****");
        });

        // Get all nodes of cluster in separate variables
        CorfuApplicationServer server0 = corfuCluster.getFirstServer();
        CorfuApplicationServer server1 = corfuCluster.getServerByIndex(1);
        CorfuApplicationServer server2 = corfuCluster.getServerByIndex(2);

        // Stop one node and wait for layout's unresponsive servers to change
        log.info("**** Disconnect node server0 ****");
        server0.disconnect(Arrays.asList(server1, server2));
        log.info("**** Verify layout after disconnecting server0 ****");
        waitForLayoutChange(layout -> layout.getUnresponsiveServers()
                .equals(Collections.singletonList(server0.getEndpoint())), corfuClient);
        // Verify cluster status is DEGRADED
        log.info("**** Verify cluster status is DEGRADED after disconnecting server0 ****");
        waitForClusterStatusDegraded(corfuClient);

        // Add 100 more entries in table
        log.info("**** Add 2nd set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(100, 200, uuids, events, txn, false));
        helper.transactional((utils, txn) -> {
            // Verify table row count (should be 200)
            utils.verifyTableRowCount(txn, 200);
            log.info("**** Second Insertion Verification:: Verify Table Data one by one ****");
            utils.verifyTableData(txn, 0, 200, false);
            log.info("**** Second Insertion Verified... ****");
        });

        //Update table records from 51 to 150
        log.info("**** Update the records ****");
        helper.transactional((utils, txn) -> utils.generateData(51, 150, uuids, events, txn, true));
        helper.transactional((utils, txn) -> {
            // Verify table row count (should be 200)
            utils.verifyTableRowCount(txn, count * 2);
            log.info("**** Record Updation Verification:: Verify Table Data one by one ****");
            utils.verifyTableData(txn, 51, 150, true);
            log.info("**** Record Updation Verified ****");
        });

        log.info("**** Reconnect node server0 ****");
        server0.reconnect(Arrays.asList(server1, server2));
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
        // Verify cluster status is STABLE
        log.info("**** Verify cluster status :: after restarting node and removing partition ****");
        waitForClusterStatusStable(corfuClient);

        // Add 100 more entries in table
        log.info("**** Add 3rd set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(200, 300, uuids, events, txn, false));
        helper.transactional((utils, txn) -> {
            // Verify table row count (should be 300)
            utils.verifyTableRowCount(txn, count * 3);
            // Verify all data in table
            log.info("**** Third Insertion Verification: Verify Table Data one by one ****");
            utils.verifyTableData(txn, count * 2, count * 3, false);
            utils.verifyTableData(txn, 151, count * 3, false);
            utils.verifyTableData(txn, 51, 150, true);
            log.info("**** Third Insertion Verified ****");
        });

        // Clear table data and verify
        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
