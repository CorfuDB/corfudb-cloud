package org.corfudb.test.vm.stateful.ufo.kill;

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
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.UniverseConfigurator;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class KillServiceOnOneNodeFiveHundredTimesTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 500;
    private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();
    private final UniverseManager universeManager = configurator.universeManager;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after kill service on one node
     * 1)  Deploy and bootstrap a three nodes cluster
     * 2)  Create a table in corfu
     * 3)  Repeat the steps from (4 - 10) in loop, LOOP_COUNT i.e 500
     * 4)  Get node Index randomly to stop "corfu" service on that node
     * 5)  Stop the "corfu" service them wait till cluster goes into "DEGRADED" state
     * 6)  Add 100 Entries into table
     * 7)  if (LOOP_COUNT mod 2) equals to 0 then update the table entries
     * 8)  Verify the table rows and its contents
     * 9)  Start the "corfu" service and wait till cluster status become "STABLE"
     * 10) Verify the table rows and its contents
     * 11) Once the loop is over, verify all data
     * 12) Clear the table contents
     */
    @Test
    public void test() {

        universeManager.workflow(wf -> {
            wf.setupVm(configurator.vmSetup);
            wf.setupVm(fixture -> {
                //don't stop corfu cluster after the test
                fixture.getUniverse().cleanUpEnabled(false);
            });
            wf.initUniverse();
            try {
                verifyKillServiceOnOneNodeFiveHundredTimesTest(wf);
            } catch (Exception e) {
                fail("Failed", e);
            }
        });
    }

    private void verifyKillServiceOnOneNodeFiveHundredTimesTest(
            UniverseWorkflow<Fixture<UniverseParams>> wf) throws Exception {

        // create instance of Random class
        Random rand = new SecureRandom();
        int start = 0;
        int end;
        int count = 100;
        int rindex;
        boolean isTrue = false;

        UniverseParams params = wf.getFixture().data();
        CorfuCluster corfuCluster = wf.getUniverse()
                .getGroup(params.getGroupParamByIndex(0).getName());
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
        String tableName = "CorfuUFO_KillServiceOnOneNodeFiveHundredTimesTable";

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

        // Fetch timestamp to perform snapshot queries or transactions at a particular timestamp.
        corfuStore.getTimestamp();

        for (int lcount = 1; lcount < LOOP_COUNT; lcount++) {
            // get the random node
            rindex = rand.nextInt(3);
            log.info(String.format(" *** rindex value is: %s ***", rindex));
            CorfuServer server = corfuCluster.getServerByIndex(rindex);

            // kill corfu service and wait for layout's unresponsive servers to change
            // & cluster to become DEGRADED
            log.info(String.format("*** killing service on node:: %s ***", rindex));
            server.kill();
            waitForUnresponsiveServersChange(size -> size == 1, corfuClient);
            waitForClusterStatusDegraded(corfuClient);

            // data insertion into the table
            end = count * lcount;
            log.info("*********************");
            log.info("*** required values like start::{}, end::{} and lcount::{}",
                    start, end, lcount);
            log.info("*********************");
            log.info("*** insert the 100 enteries inot the table ***");
            UfoUtils.generateDataAndCommit(
                    start, end, tableName, uuids, events, tx, metadata, isTrue
            );

            if (lcount % 2 == 0) {
                isTrue = true;
                log.info(" *** updating the records with start::{}, end::{} and lcount::{} ***",
                        start, end, lcount);
                UfoUtils.generateDataAndCommit(
                        start, end, tableName, uuids, events, tx, metadata, isTrue
                );
            }

            // verification of table rows and it's content one by one
            log.info("*** verify the rows count that should be {} ***", count * lcount);
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * lcount);
            log.info("table has {} rows as expected", count * lcount);
            UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, isTrue);

            // start the service and wait for cluster to become stable
            log.info(String.format("**** start service on node:: %s ****", rindex));
            server.start();
            waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
            log.info("**** after starting service, checking cluster status ****");
            waitForClusterStatusStable(corfuClient);
            log.info(" *** cluster status is stable after starting the service ***");

            // verification of table rows and it's content one by one
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * lcount);
            UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, isTrue);
            log.info(String.format("**** %s:: verification done ****", lcount));

            start = end;
            isTrue = false;

        }

        log.info(" *** at last, verifying the entire table contents one by one ***");
        start = 1;
        for (int idx = 1; idx < LOOP_COUNT; idx++) {
            end = count * idx;
            if (idx % 2 == 0) {
                log.info("*** verifying updated data ***");
                log.info("*** required values like start::{}, end::{} and lcount::{}",
                        start, end, idx);
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, true);
            } else {
                log.info("*** verifying non-updated data ***");
                log.info("*** required values like start::{}, end::{} and lcount::{}",
                        start, end, idx
                );
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, false);
            }
            start = end;
        }

        log.info("*** clearing up the table contents ***");
        Query q = corfuStore.query(manager);
        UfoUtils.clearTableAndVerify(table, tableName, q);
    }
}
