package org.corfudb.test.vm.stateful.ufo;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.ClientParams;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.Universe.UniverseMode;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForStandaloneNodeClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
@Tag(TestGroups.BAT)
@Tag(TestGroups.STATEFUL)
public class ClusterDetachRejoinTwoNodesTest extends AbstractCorfuUniverseTest {
    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after add/remove nodes
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Create a table in corfu
     * 3) Add 100 Entries into table and verify count and data of table
     * 4) Remove two nodes from cluster
     * 5) Verify Layout
     * 6) Add 100 more Entries into table and verify count and data of table
     * 7) Reattach the two detached nodes into cluster
     * 8) Verify Layout
     * 9) Update Records from 60 to 139 index and Verify
     * 10) Verify the table contents and updated data
     * 11) Clear the table and verify table contents are cleared
     */

    @Test
    public void test() {
        testRunner.executeTest(this::verifyClusterDetachRejoin);
    }

    private void verifyClusterDetachRejoin(UniverseWorkflow<Fixture<UniverseParams>> wf)
            throws Exception {
        UniverseParams params = wf.getFixture().data();

        ClientParams clientFixture = ClientParams.builder().build();

        CorfuCluster corfuCluster = wf.getUniverse()
                .getGroup(params.getGroupParamByIndex(0).getName());

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String namespace = "manager";
        // Define table name
        String tableName = getClass().getSimpleName();

        log.info("Verify cluster status is stable");
        waitForClusterStatusStable(corfuClient);

        final Table<IdMessage, EventInfo, ManagedResources> table = UfoUtils.createTable(
                corfuStore, namespace, tableName
        );

        final int count = 100;
        final List<IdMessage> uuids = new ArrayList<>();
        final List<EventInfo> events = new ArrayList<>();
        final ManagedResources metadata = ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        TxBuilder tx = corfuStore.tx(namespace);

        final Query q = corfuStore.query(namespace);
        UfoUtils.clearTableAndVerify(table, tableName, q);
        UfoUtils.generateDataAndCommit(0, count, tableName, uuids, events, tx, metadata, false);

        log.info("First Verification:: Verify table row count");
        UfoUtils.verifyTableRowCount(corfuStore, namespace, tableName, count);
        log.info("First Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, count, namespace, tableName, false);
        log.info("First Verification:: Completed");

        CorfuServer server0 = corfuCluster.getFirstServer();

        List<CorfuServer> servers = Arrays.asList(
                corfuCluster.getServerByIndex(1),
                corfuCluster.getServerByIndex(2)
        );

        //should remove two nodes from corfu cluster
        {
            log.info("Detaching Two Nodes...");
            // Sequentially remove two nodes from cluster
            for (CorfuServer candidate : servers) {
                log.info("Removing Node: {}", candidate);
                corfuClient.getManagementView().removeNode(
                        candidate.getEndpoint(),
                        clientFixture.getNumRetry(),
                        clientFixture.getTimeout(),
                        clientFixture.getPollPeriod()
                );
            }

            log.info("Check Cluster status of Detached Nodes");
            for (CorfuServer candidate : servers) {
                log.info("Cluster status check of Node: {}", candidate);
                // Check cluster status of detached node
                waitForStandaloneNodeClusterStatusStable(corfuClient, candidate);
            }

            log.info("Verify Layout After Detaching Two Nodes");
            // Verify layout contains only the node that is not removed
            corfuClient.invalidateLayout();
            assertThat(corfuClient.getLayout().getAllServers())
                    .containsExactly(server0.getEndpoint());

            log.info("insert 100 more rows into table");
            UfoUtils.generateDataAndCommit(
                    100, count * 2, tableName, uuids, events, tx, metadata, false
            );
            log.info("Second Verification:: Verify table row count");
            UfoUtils.verifyTableRowCount(corfuStore, namespace, tableName, count * 2);
            log.info("Second Verification:: Verify the data");
            UfoUtils.verifyTableData(corfuStore, 100, count * 2, namespace, tableName, false);
            log.info("Second Verification:: Completed");

            if (wf.getUniverseMode() == UniverseMode.VM) {
                waitUninterruptibly(Duration.ofSeconds(15));
            }
        }

        //should add two nodes back to corfu cluster
        {
            log.info("Add the detached nodes back to cluster...");
            // Sequentially add two nodes back into cluster
            for (CorfuServer candidate : servers) {
                corfuClient.getManagementView().addNode(
                        candidate.getEndpoint(),
                        clientFixture.getNumRetry(),
                        clientFixture.getTimeout(),
                        clientFixture.getPollPeriod()
                );
            }

            log.info("After Rejoining Cluster Verify Layout");
            // Verify layout should contain all three nodes
            corfuClient.invalidateLayout();
            assertThat(corfuClient.getLayout().getAllServers().size())
                    .isEqualTo(corfuCluster.nodes().size());

            log.info("After Rejoining the detached nodes Check cluster status");
            waitForClusterStatusStable(corfuClient);

            log.info("Update the records");
            UfoUtils.generateDataAndCommit(60, 90, tableName, uuids, events, tx, metadata, true);
            log.info("Third Verification:: Verify the data");
            UfoUtils.verifyTableData(corfuStore, 0, 60, namespace, tableName, false);
            UfoUtils.verifyTableData(corfuStore, 91, count * 2, namespace, tableName, false);
            log.info("Third Verification:: Verify the updated data");
            UfoUtils.verifyTableData(corfuStore, 60, 90, namespace, tableName, true);
            log.info("Third Verification:: Completed");

            UfoUtils.clearTableAndVerify(table, tableName, q);

        }
    }

}
