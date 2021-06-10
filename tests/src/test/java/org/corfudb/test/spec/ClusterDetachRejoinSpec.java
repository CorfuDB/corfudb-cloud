package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.runtime.collections.TxnContext;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.ClientParams;
import org.corfudb.universe.universe.node.client.CorfuClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
 * <p>
 * Test cluster behavior after add/remove nodes
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Create a table in corfu
 * 3) Add 100 Entries into table and verify count and data of table
 * 4) Remove one node from cluster
 * 5) Verify Layout
 * 6) Add 100 more Entries into table and verify count and data of table
 * 7) Update Records from 60 to 139 index and Verify
 * 8) Add node back into cluster
 * 9) Verify Layout
 * 10) Add 100 more Entries into table and verify count and data of table
 * 11) Clear the table and verify table contents are cleared
 */
@Slf4j
public class ClusterDetachRejoinSpec {

    /**
     * verifyClusterDetachRejoin
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void clusterDetachRejoin(
            U wf) throws Exception {

        ClientParams clientFixture = ClientParams.builder().build();

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);

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

        // Create & Register the table.
        // This is required to initialize the table for the current corfu client.

        Table<IdMessage, EventInfo, ManagedResources> table = UfoUtils.createTable(
                corfuStore, manager, tableName
        );

        final int count = 100;
        List<IdMessage> uuids = new ArrayList<>();
        List<EventInfo> events = new ArrayList<>();
        ManagedResources metadata = ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();

        // Creating a transaction builder.
        TxnContext txn = corfuStore.txn(manager);

        // Fetch timestamp to perform snapshot queries or transactions at a particular timestamp.
        corfuStore.getTimestamp();

        UfoUtils.generateDataAndCommit(0, count, table, uuids, events, txn, metadata, false);
        Query q = corfuStore.query(manager);

        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count);

        log.info("First Insertion VErification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, count, manager, tableName, false);

        log.info("First Insertion Verified...");

        //Detach One node from Cluster
        CorfuApplicationServer server = corfuCluster.getServerByIndex(2);

        //should remove one node from corfu cluster
        {
            log.info("Detach One Node...");
            corfuClient.getManagementView().removeNode(
                    server.getEndpoint(),
                    clientFixture.getNumRetry(),
                    clientFixture.getTimeout(),
                    clientFixture.getPollPeriod());
        }

        log.info("Reset the Detached Node...");
        // Reset the detached node so that we do not end up with an OverwriteException.
        corfuClient.getRuntime().getLayoutView().getRuntimeLayout()
                .getBaseClient(server.getEndpoint()).reset();

        log.info("Verify Layout After Detaching One Node");
        // Verify layout contains only the node that is not removed
        corfuClient.invalidateLayout();
        assertThat(corfuClient.getLayout().getAllServers())
                .doesNotContain(server.getEndpoint());

        waitUninterruptibly(Duration.ofSeconds(15));

        UfoUtils.generateDataAndCommit(100, 200, tableName, uuids, events, tx, metadata, false);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, 200);

        log.info("Second Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, 200, manager, tableName, false);


        log.info("Second Insertion Verified...");


        //Update table records from 60 to 139
        log.info("Update the records");
        UfoUtils.generateDataAndCommit(60, 140, tableName, uuids, events, tx, metadata, true);
        UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * 2);

        log.info("Third Insertion Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 60, 140, manager, tableName, true);


        //should add the node back to corfu cluster
        {
            log.info("Add the detached node back to cluster...");
            // Add the detached node back into cluster
            corfuClient.getManagementView().addNode(
                    server.getEndpoint(),
                    clientFixture.getNumRetry(),
                    clientFixture.getTimeout(),
                    clientFixture.getPollPeriod()
            );

            log.info("After Rejoining Cluster Verify Layout");
            // Verify layout should contain all three nodes
            corfuClient.invalidateLayout();
            assertThat(corfuClient.getLayout().getAllServers().size())
                    .isEqualTo(corfuCluster.nodes().size());

            log.info("After Rejoining the detached nodes Check cluster status");
            waitForClusterStatusStable(corfuClient);

            UfoUtils.generateDataAndCommit(200, 300, tableName, uuids, events, tx, metadata, false);
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * 3);

            log.info("Fourth Insertion Verification: Verify Table Data one by one");
            UfoUtils.verifyTableData(corfuStore, count * 2, count * 3, manager, tableName, false);
            UfoUtils.verifyTableData(corfuStore, 141, count * 3, manager, tableName, false);
            UfoUtils.verifyTableData(corfuStore, 60, 140, manager, tableName, true);


            UfoUtils.clearTableAndVerify(table, tableName, q);

        }
    }
}
