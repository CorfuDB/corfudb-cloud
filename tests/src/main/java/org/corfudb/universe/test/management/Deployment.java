package org.corfudb.universe.test.management;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.UniverseManager;
import org.corfudb.universe.api.workflow.UniverseWorkflow.WorkflowConfig;
import org.corfudb.universe.test.UniverseConfigurator;

import static org.corfudb.universe.test.UniverseConfigurator.getServerVersion;

@Slf4j
public class Deployment {

    private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();

    /**
     * Deploying a corfu cluster:
     * - disable shutdown logic to prevent the universe stop and clean corfu servers
     * - deploy corfu server
     */
    public static void main(String[] args) {
        log.info("Deploying corfu cluster...");

        Deployment deployment = new Deployment();
        deployment.deploy();

        log.info("Corfu cluster has deployed");

        System.exit(0);
    }

    private Deployment deploy() {
        WorkflowConfig config = WorkflowConfig.builder()
                .testName("corfu_stateful_cluster")
                .corfuServerVersion(getServerVersion())
                .build();

        UniverseManager universeManager = UniverseManager.builder()
                .config(config)
                .build();

        universeManager.vmWorkflow(wf -> {
            wf.setup(configurator.vmSetup);
            //disable shutdown logic
            wf.setup(fixture -> fixture.getUniverse().cleanUpEnabled(false));
            wf.deploy();
        });

        return this;
    }
}
