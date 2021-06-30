package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.failure.NodeFailure;
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
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
 * <p>
 * Test cluster behavior after one is down
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Create a table in corfu
 * 3) Add 100 Entries into table and verify count and data of table
 * 4) Stop a node
 * 5) Verify layout, cluster status is DEGRADED
 * 6) Add 100 more Entries into table and verify count and data of table
 * 7) Start the node
 * 8) Verify layout, cluster status and data path again
 * 9) Update Records from 60 to 139 index and Verify
 * 10) Clear the table and verify table contents are cleared
 */
@Slf4j
public class OneNodeDownSpec implements GenericSpec {

    /**
     * oneNodeDownTest
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void oneNodeDownTest(
            U wf) throws Exception {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("Check cluster status");
        waitForClusterStatusStable(corfuClient);

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Define table name
        String tableName = getClass().getSimpleName();

        final int count = 100;
        List<IdMessage> uuids = new ArrayList<>();
        List<EventInfo> events = new ArrayList<>();

        SpecHelper helper = new SpecHelper(runtime, tableName);

        helper.transactional((utils, txn) -> {
            // Add the entries in Table
            utils.generateData(0, count, uuids, events, txn, false);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, count);
            log.info("First Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, count, false);
            log.info("First Insertion Verified...");
        });

        //Should stop one node and then restart
        CorfuApplicationServer server0 = corfuCluster.getFirstServer();

        NodeFailure oneNodeFailure = new NodeFailure(corfuClient, server0);
        oneNodeFailure.failure((client, server) -> {
            // Add the entries again in Table
            log.info("Add data into table");
            helper.transactional((utils, txn) -> {
                utils.generateData(100, 200, uuids, events, txn, false);
            });

            helper.transactional((utils, txn) -> {
                utils.verifyTableRowCount(txn, 200);
                log.info("Second Insertion Verification:: Verify Table Data one by one");
                utils.verifyTableData(txn, 0, 200, false);
                log.info("Second Insertion Verified...");
            });
        });

        //Update table records from 60 to 139
        log.info("Update the records");
        helper.transactional((utils, txn) -> {
            utils.generateData(60, 140, uuids, events, txn, true);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, count * 2);
            log.info("Third Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 60, 140, true);
            log.info("Third Insertion Verified...");
        });

        log.info("Clear the Table");
        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
