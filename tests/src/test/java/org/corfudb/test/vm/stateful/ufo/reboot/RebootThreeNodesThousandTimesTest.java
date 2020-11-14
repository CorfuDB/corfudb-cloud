package org.corfudb.test.vm.stateful.ufo.reboot;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.infrastructure.vm.universe.node.server.VmCorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class RebootThreeNodesThousandTimesTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 1000;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after Reboot all cluster nodes
     * 1)  Deploy and bootstrap a three nodes cluster
     * 2)  Create a table in corfu
     * 3)  Repeat the steps from (4 - 9) in loop, LOOP_COUNT i.e 1000
     * 4)  Add 100 Entries into table
     * 5)  if (LOOP_COUNT mod 2) equals to 0 then update the table entries
     * 6)  Verify the table rows and its contents
     * 7)  Reboot all cluster nodes
     * 8)  Start the "corfu" process on all cluster nodes and wait till cluster status become "STABLE"
     * 9)  Verify the table rows and its content
     * 10)  Once the loop is over, verify all data
     * 11)  Clear the table contents
     */
    @Test
    public void test() {
        testRunner.executeStatefulVmTest(this::verifyRebootThreeNodesThousandTimesTest);
    }

    private void verifyRebootThreeNodesThousandTimesTest(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf)
            throws Exception {


        int start = 0;
        int end;
        int count = 100;
        int rindex;
        boolean isTrue = false;

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("**** before running testcase, verify cluster status ****");
        waitForClusterStatusStable(corfuClient);
        log.info("*** cluster status is STABLE, executing testcase ***");

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String manager = "manager";
        // Define table name
        String tableName = getClass().getSimpleName();

        // Create & Register the table.
        // This is required to initialize the table for the current corfu client.
        final Table<IdMessage, EventInfo, ManagedResources> table = UfoUtils.createTable(
                corfuStore, manager, tableName
        );

        List<TestSchema.IdMessage> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
        TestSchema.ManagedResources metadata = TestSchema.ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        final TxBuilder tx = corfuStore.tx(manager);

        for (int lcount = 1; lcount < LOOP_COUNT; lcount++) {

            end = count * lcount;
            log.info("*********************");
            log.info(String.format("*** required values like start::%s, end::%s and lcount::%s", start, end, lcount));
            log.info("*********************");
            log.info("*** insert the 100 enteries inot the table ***");
            UfoUtils.generateDataAndCommit(start, end, tableName, uuids, events, tx, metadata, isTrue);

            if (lcount % 2 == 0) {
                isTrue = true;
                log.info(" *** updating the records with start::{}, end::{} and lcount::{} ***", start, end, lcount);
                UfoUtils.generateDataAndCommit(start, end, tableName, uuids, events, tx, metadata, isTrue);
            }

            // verification of table rows and it's content one by one
            log.info(String.format("*** verify the rows count that should be %s ***", count * lcount));
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * lcount);
            log.info(String.format("table has %s rows as expected", count * lcount));
            UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, isTrue);

            // Reboot all cluster nodes
            for (int index = 0; index < 3; index++) {
                log.info(String.format("*** Rebooting %s node ***", index));
                VmCorfuServer vmServer = (VmCorfuServer) corfuCluster.getServerByIndex(index);
                vmServer.getVmManager().reboot();
            }

            // wait for 40sec after reboot the cluster nodes
            log.info(" *** wait for 40sec after reboot the cluster nodes *** ");
            waitUninterruptibly(Duration.ofSeconds(40));

            // start the 'corfu' process on all cluster nodes
            for (int index = 0; index < 3; index++) {
                log.info(String.format("*** starting 'corfu' process on %s node of cluster ***", index));
                VmCorfuServer vmServer = (VmCorfuServer) corfuCluster.getServerByIndex(index);
                vmServer.start();
            }

            waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
            log.info("*** wait for cluster status to become STABLE ***");
            waitForClusterStatusStable(corfuClient);

            // verification of table rows and it's content one by one
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * lcount);
            UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, isTrue);
            log.info(String.format("**** %s:: verification done ****", lcount));

            start = end;
            isTrue = false;

        }

        log.info(" *** at last, verifying the entire table contents on by one ***");
        start = 1;
        for (int idx = 1; idx < LOOP_COUNT; idx++) {
            end = count * idx;
            if (idx % 2 == 0) {
                log.info("*** verifying updated data ***");
                log.info(String.format("*** required values like start::%s, end::%s and lcount::%s", start, end, idx));
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, true);
            } else {
                log.info("*** verifying non-updated data ***");
                log.info(String.format("*** required values like start::%s, end::%s and lcount::%s", start, end, idx));
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, false);
            }
            start = end;
        }

        log.info("*** clearing up the table contents ***");
        Query q = corfuStore.query(manager);
        UfoUtils.clearTableAndVerify(table, tableName, q);

    }
}