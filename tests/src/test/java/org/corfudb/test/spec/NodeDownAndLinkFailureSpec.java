package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.test.spec.api.GenericSpec.SpecHelper;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.corfudb.test.TestSchema.EventInfo;
import static org.corfudb.test.TestSchema.IdMessage;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForNextEpoch;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
 * <p>
 * Test cluster behavior after one node down and one link failure
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Create a table in corfu
 * 3) Add 100 Entries into table and verify count and data of table
 * 4) Stop one node
 * 5) Create a link failure between two nodes which results in a partial partition
 * 6) Verify layout, cluster status and data path
 * 7) Add 100 more Entries into table and verify count and data of table
 * 8) Update Records from 51 to 150 index and verify
 * 9) Start the stopped node
 * 10) Verify layout, cluster status and data path
 * 11) Remove the link failure
 * 12) Verify layout, cluster status and data path again
 * 13) Add 100 more Entries into table and verify count and data of table
 * 14) Clear the table and verify table contents are cleared
 */
@Slf4j
public class NodeDownAndLinkFailureSpec {

    /**
     * verifyNodeDownAndLinkFailure
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void downAndLinkFailure(
            U wf) throws Exception {
        CorfuCluster<DeploymentParams<CorfuServerParams>, CorfuApplicationServer> corfuCluster =
                wf.getUniverse().getGroup(ClusterType.CORFU);
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);

        CorfuRuntime runtime = corfuClient.getRuntime();

        // Define table name
        String tableName = getClass().getSimpleName();

        SpecHelper helper = new SpecHelper(runtime, tableName);

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

        long currEpoch = corfuClient.getLayout().getEpoch();

        log.info("**** Stop server2 ****");
        server2.stop(Duration.ofSeconds(10));
        log.info("**** Wait for layout's unresponsive servers to change ****");
        waitForNextEpoch(corfuClient, currEpoch + 1);
        assertThat(corfuClient.getLayout().getUnresponsiveServers()).containsExactly(server2.getEndpoint());

        // Create link failure between server0 and server1
        // After this, cluster becomes unavailable.
        // NOTE: cannot use waitForClusterDown() since the partition only happens on server side, client
        // can still connect to two nodes, write to table so system down handler will not be triggered.
        log.info("**** Create link failure between server0 and server1 ****");
        server0.disconnect(Collections.singletonList(server1));

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

        // Restart the stopped node
        log.info("**** Restart the stopped on node server2 ****");
        server2.start();

        log.info("**** Wait for cluster status become DEGRADED ****");
        waitUninterruptibly(Duration.ofSeconds(30));
        waitForClusterStatusDegraded(corfuClient);

        log.info("**** Repair the partition between server0 and server1 ****");
        server0.reconnect(Collections.singletonList(server1));
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
        // Verify cluster status is STABLE
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
        });

        // Clear table data and verify
        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
