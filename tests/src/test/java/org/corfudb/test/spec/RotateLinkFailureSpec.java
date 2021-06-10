package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.runtime.view.Layout;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.test.spec.api.GenericSpec;
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
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForLayoutChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
 * <p>
 * Test cluster behavior when rotating link failure among nodes
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Create a table in corfu
 * 3) Add 100 Entries into table and verify count and data of table
 * 4) Create a link failure between node0 and node1
 * 5) Create a link failure between node1 and node2
 * and heal previous link failure
 * 6) Create a link failure between node2 and node0
 * and heal previous link failure
 * 7) Reverse rotation direction, create a link failure
 * between node1 and node2 and heal previous link failure
 * 8) Verify layout and data path after each rotation
 * 9) Recover cluster by removing all link failures
 * 10) Verify layout, cluster status and data path
 * 11) Add 100 more Entries into table and verify count and data of table
 * 12) Update Records from 60 to 139 index and Verify
 * 13) Clear the table and verify table contents are cleared
 */
@Slf4j
public class RotateLinkFailureSpec {

    /**
     * verifyRotateLinkFailure
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void rotateLinkFailure(
            U wf) throws Exception {

        CorfuCluster<DeploymentParams<CorfuServerParams>, CorfuApplicationServer> corfuCluster =
                wf.getUniverse().getGroup(ClusterType.CORFU);

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("Check cluster status");
        waitForClusterStatusStable(corfuClient);

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String manager = "manager";
        // Define table name
        String tableName = getClass().getSimpleName();

        final int count = 100;
        List<IdMessage> uuids = new ArrayList<>();
        List<EventInfo> events = new ArrayList<>();
        ManagedResources metadata = ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();

        // Fetch timestamp to perform snapshot queries or transactions at a particular timestamp.
        runtime.getSequencerView().query().getToken();
        GenericSpec.SpecHelper helper = new GenericSpec.SpecHelper(runtime, tableName);

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

        //Should rotate link failures among cluster
        CorfuApplicationServer server0 = corfuCluster.getFirstServer();
        CorfuApplicationServer server1 = corfuCluster.getServerByIndex(1);
        CorfuApplicationServer server2 = corfuCluster.getServerByIndex(2);

        log.info("1st link failure rotation, disconnect between server0 and server1. Current layout: {}",
                corfuClient.getLayout());

        server0.disconnect(Collections.singletonList(server1));
        Predicate<Layout> checkServer1 = layout -> {
            List<String> expected = Collections.singletonList(server1.getEndpoint());
            return layout.getUnresponsiveServers().equals(expected);
        };
        waitForLayoutChange(checkServer1, corfuClient);

        log.info("Verify Cluster status is Degraded...");
        waitForClusterStatusDegraded(corfuClient);

        // Add the entries again in Table after restart
        helper.transactional((utils, txn) -> {
            utils.generateData(100, 200, uuids, events, txn, false);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, 200);
            log.info("Second Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, 200, false);
            log.info("Second Insertion Verified...");
        });

        Layout latestLayout = corfuClient.getLayout();

        log.info("2nd link failure rotation, disconnect between server1 and server2 "
                + "and heal previous link failure between server0 and server1");
        server1.disconnect(Collections.singletonList(server2));
        server0.reconnect(Collections.singletonList(server1));

        log.info("Wait for some time to ensure cluster stabilizes Server1 should stay "
                + "in unresponsive set, no layout change");
        waitUninterruptibly(Duration.ofSeconds(30));
        assertThat(corfuClient.getLayout()).isEqualTo(latestLayout);

        log.info("Verify Cluster status is Degraded...");
        waitForClusterStatusDegraded(corfuClient);

        // Add the entries again in Table after restart
        helper.transactional((utils, txn) -> {
            utils.generateData(200, 300, uuids, events, txn, false);
        });
        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, 300);
            log.info("Third Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, 300, false);
            log.info("Third Insertion Verified...");
        });

        log.info("3rd link failure rotation, disconnect between server2 and server0 "
                + "and heal previous link failure between server1 and server2");
        server2.disconnect(Collections.singletonList(server0));
        server1.reconnect(Collections.singletonList(server2));

        log.info("Server0 and server2 has same number of link failure ie. 1, "
                + "the one with larger endpoint should be marked as unresponsive.");
        Predicate<Layout> checkServer2 = layout -> {
            List<String> expected = Collections.singletonList(server2.getEndpoint());
            return layout.getUnresponsiveServers().equals(expected);
        };
        waitForLayoutChange(checkServer2, corfuClient);

        log.info("Verify data path working fine");
        waitUninterruptibly(Duration.ofSeconds(20));

        log.info("Verify Cluster status is Degraded...");
        waitForClusterStatusDegraded(corfuClient);

        // Add the entries again in Table after restart
        helper.transactional((utils, txn) -> {
            utils.generateData(300, 400, uuids, events, txn, false);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, 400);
            log.info("Fourth Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, 400, false);
            log.info("Fourth Insertion Verified...");
        });

        log.info("4th link failure rotation, reverse the rotating direction, "
                + "disconnect between server1 and server2 "
                + "and heal previous link failure between server1 and server2");
        server1.disconnect(Collections.singletonList(server2));
        server2.reconnect(Collections.singletonList(server0));

        log.info("Wait for some time to ensure cluster stabilizes "
                + "Server1 should stay in unresponsive set, no layout change");
        waitUninterruptibly(Duration.ofSeconds(30));

        log.info("Verify Cluster status is Degraded...");
        waitForClusterStatusDegraded(corfuClient);

        // Add the entries again in Table after restart
        helper.transactional((utils, txn) -> {
            utils.generateData(400, 500, uuids, events, txn, false);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, 500);
            log.info("Fifth Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, 500, false);
            log.info("Fifth Insertion Verified...");
        });

        log.info("Finally stop rotation and heal all link failures.");
        server1.reconnect(Collections.singletonList(server2));
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);

        // Verify cluster status is STABLE
        log.info("Verify cluster status at the end of test");
        waitForClusterStatusStable(corfuClient);

        // Add the entries again in Table after restart
        helper.transactional((utils, txn) -> {
            utils.generateData(500, 600, uuids, events, txn, false);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, 600);
            log.info("Sixth Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 0, 600, false);
            log.info("Sixth Insertion Verified...");
        });

        log.info("Update the records");
        helper.transactional((utils, txn) -> {
            utils.generateData(60, 140, uuids, events, txn, false);
        });

        helper.transactional((utils, txn) -> {
            utils.verifyTableRowCount(txn, count * 6);
            log.info("Third Insertion Verification:: Verify Table Data one by one");
            utils.verifyTableData(txn, 60, 140, false);
            log.info("Third Insertion Verified...");
        });

        log.info("Clear the Table");
        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
