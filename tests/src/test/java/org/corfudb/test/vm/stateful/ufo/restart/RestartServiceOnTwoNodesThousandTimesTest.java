package org.corfudb.test.vm.stateful.ufo.restart;

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
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.corfudb.universe.universe.node.server.corfu.ApplicationServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class RestartServiceOnTwoNodesThousandTimesTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 1000;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after service restarted on two nodes 1000 times
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Verify Cluster is stable after deployment
     * 3) Create a table in corfu
     * 4) Repeat the steps from (5 - 9) in loop, LOOP_COUNT i.e 1000
     * 5) Add 100 Entries into table
     * 6) if (LOOP_COUNT mod 2) equals to 0 then update the table entries
     * 7) Verify the table rows and its contents
     * 8) Restart the "corfu" service on two nodes (rindex node and 3rd node)
     * 9) Again verify the table rows and its contents
     * 10) Once the loop is over, verify all data
     * 11) clear the table contents
     */
    @Test
    public void test() {
        testRunner.executeStatefulVmTest(this::verifyRestartService);
    }

    private void verifyRestartService(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf)
            throws InterruptedException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {

        // create instance of Random class
        Random rand = new SecureRandom();
        int start;
        int end;
        int rindex;
        start = 0;
        boolean isTrue = false;

        CorfuCluster corfuCluster = wf.getUniverse()
                .getGroup(wf.getFixture().data().getGroupParamByIndex(0).getName());

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String manager = "manager";
        // Define table name
        String tableName = getClass().getSimpleName();

        //Check CLUSTER STATUS
        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);

        final Table<IdMessage, EventInfo, ManagedResources> table = UfoUtils.createTable(
                corfuStore, manager, tableName
        );

        final int count = 100;
        List<TestSchema.IdMessage> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
        TestSchema.ManagedResources metadata = TestSchema.ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        final TxBuilder tx = corfuStore.tx(manager);

        // Loop for 1000 times restart service on two nodes serially
        for (int loopCount = 1; loopCount <= LOOP_COUNT; loopCount++) {

            // data insertion into the table
            end = count * loopCount;
            log.info(String.format("**** required values like start::%s, end::%s and loopCount::%s ****",
                    start, end, loopCount));
            log.info("**** Insert the 100 enteries into the table ****");
            UfoUtils.generateDataAndCommit(start, end, tableName, uuids, events, tx, metadata, isTrue);

            if (loopCount % 2 == 0) {
                isTrue = true;
                log.info(String.format("**** Updating the records with start::%s, end::%s and loopCount::%s ****",
                        start, end, loopCount));
                UfoUtils.generateDataAndCommit(start, end, tableName, uuids, events, tx, metadata, isTrue);
            }

            // verification of table rows and it's content one by one
            log.info(String.format("**** Verify the rows count that should be %s ****", count * loopCount));
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * loopCount);
            log.info(String.format("**** Table has %s rows as expected ****", count * loopCount));
            UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, isTrue);

            // Restart two nodes and wait for cluster become stable
            rindex = rand.nextInt(2);
            ApplicationServer server = corfuCluster.getServerByIndex(rindex);
            log.info(String.format("**** Restarting server%s ****", rindex));
            server.restart();
            ApplicationServer server2 = corfuCluster.getServerByIndex(2);
            log.info("**** Restarting server2 ****");
            server2.restart();
            waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
            waitUninterruptibly(Duration.ofSeconds(30));
            log.info("**** Wait for cluster status STABLE :: after restarting service on all servers ****");
            waitForClusterStatusStable(corfuClient);

            // verification of table rows and it's content one by one
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * loopCount);
            UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, isTrue);
            log.info(String.format("**** %s :: verification done ****", loopCount));

            start = end;
            isTrue = false;
        }

        log.info("**** Verifying the entire table contents one by one ****");
        start = 1;
        for (int index = 1; index < LOOP_COUNT; index++) {
            end = count * index;
            if (index % 2 == 0) {
                log.info("**** Verifying updated data ****");
                log.info(String.format("**** Required values like start::%s, end::%s and loopCount::%s ****",
                        start, end, index));
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, true);
            } else {
                log.info("**** Verifying non-updated data ****");
                log.info(String.format("**** Required values like start::%s, end::%s and loopCount::%s ****",
                        start, end, index));
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, false);
            }
            start = end;
        }

        // Clear table data and verify
        log.info("**** Clear all data from table ****");
        Query queryObj = corfuStore.query(manager);
        UfoUtils.clearTableAndVerify(table, tableName, queryObj);
    }
}
