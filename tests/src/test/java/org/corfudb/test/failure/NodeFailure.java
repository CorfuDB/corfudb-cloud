package org.corfudb.test.failure;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.universe.node.client.CorfuClient;

import java.time.Duration;

import static org.corfudb.universe.test.util.ScenarioUtils.verifyNodeStatusIsDown;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;

/**
 * Stops a corfu server and brings it back
 */
@Slf4j
@AllArgsConstructor
public class NodeFailure {

    @NonNull
    private final CorfuClient corfuClient;
    @NonNull
    private final CorfuApplicationServer server;

    /**
     * Stops a corfu node
     * @throws Exception exception
     */
    public void failure() throws Exception {
        // Stop one node and wait for layout's unresponsive servers to change
        log.info("Stop one node");
        server.stop(Duration.ofSeconds(10));

        log.info("Verify responsive servers");
        waitForUnresponsiveServersChange(size -> size == 1, corfuClient);

        // Verify cluster status is DEGRADED with one node down
        log.info("Verify Cluster status is Degraded...");
        waitForClusterStatusDegraded(corfuClient);

        log.info("Verify that node is down");
        verifyNodeStatusIsDown(corfuClient, server);
    }

    /**
     * Starts the corfu server
     * @throws Exception exception
     */
    public void recover() throws Exception {
        // restart the stopped node and wait for layout's unresponsive servers to change
        log.info("Start the paused server now");
        server.start();

        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);

        // Verify cluster status is STABLE
        log.info("Verify cluster status");
        waitForClusterStatusStable(corfuClient);
    }
}
