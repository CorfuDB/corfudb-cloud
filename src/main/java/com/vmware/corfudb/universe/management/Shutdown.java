package com.vmware.corfudb.universe.management;

import com.vmware.corfudb.universe.UniverseConfigurator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Shutdown {

    private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();

    /**
     * Shutdown corfu cluster: stop corfu processes and delete corfu directory.
     *
     * @param args args
     */
    public static void main(String[] args) {
        log.info("Corfu cluster is being shutdown...");
        new Shutdown().shutdown();
        log.info("Corfu cluster shutdown has finished ");

        System.exit(0);
    }

    private Shutdown shutdown() {
        configurator.universeManager.workflow(wf -> {
            wf.setupVm(configurator.vmSetup);
            wf.initUniverse();
            wf.shutdown();
        });

        return this;
    }
}
