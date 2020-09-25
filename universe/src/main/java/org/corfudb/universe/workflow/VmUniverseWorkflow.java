package org.corfudb.universe.workflow;

import lombok.Builder;
import lombok.Getter;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.logging.LoggingParams;
import org.corfudb.universe.scenario.fixture.Fixtures.VmFixtureContext;
import org.corfudb.universe.scenario.fixture.Fixtures.VmUniverseFixture;
import org.corfudb.universe.universe.vm.ApplianceManager;
import org.corfudb.universe.universe.vm.VmUniverse;

@Builder
public class VmUniverseWorkflow implements UniverseWorkflow<VmFixtureContext, VmUniverseFixture> {
    @Getter
    private final WorkflowContext<VmFixtureContext, VmUniverseFixture> context;

    @Override
    public UniverseWorkflow<VmFixtureContext, VmUniverseFixture> initUniverse() {
        if (context.isInitialized()) {
            return this;
        }

        VmFixtureContext fixtureContext = context.getFixture().data();

        ApplianceManager manager = ApplianceManager.builder()
                .vSphereParams(fixtureContext.getVSphereParams())
                .build();

        LoggingParams loggingParams = context.getFixture()
                .getLogging()
                .testName(context.getConfig().getTestName())
                .build();

        //Assign universe variable before deploy prevents resources leaks
        VmUniverse universe = VmUniverse.builder()
                .universeParams(fixtureContext.getUniverseParams())
                .loggingParams(loggingParams)
                .applianceManager(manager)
                .build();
        context.setUniverse(universe);

        context.setInitialized(true);

        return this;
    }

    @Override
    public UniverseWorkflow<VmFixtureContext, VmUniverseFixture> init() {
        context.getFixture().getCluster().serverVersion(context.getConfig().getCorfuServerVersion());
        return this;
    }
}
