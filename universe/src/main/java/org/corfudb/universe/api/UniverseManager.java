package org.corfudb.universe.api;

import lombok.Builder;
import lombok.NonNull;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.workflow.UniverseWorkflow.WorkflowConfig;
import org.corfudb.universe.api.workflow.UniverseWorkflow.WorkflowContext;
import org.corfudb.universe.scenario.fixture.Fixtures.UniverseFixture;
import org.corfudb.universe.scenario.fixture.Fixtures.VmFixtureContext;
import org.corfudb.universe.scenario.fixture.Fixtures.VmUniverseFixture;
import org.corfudb.universe.infrastructure.docker.workflow.DockerUniverseWorkflow;
import org.corfudb.universe.infrastructure.process.workflow.ProcessUniverseWorkflow;
import org.corfudb.universe.infrastructure.vm.workflow.VmUniverseWorkflow;

import java.util.function.Consumer;

/**
 * Manages UniverseWorkflow and provides api to build a universe workflow.
 */
@Builder
public class UniverseManager {

    @NonNull
    private final WorkflowConfig config;

    /**
     * Creates a universe workflow.
     *
     * @param action testing logic
     * @return universe workflow
     */
    public DockerUniverseWorkflow dockerWorkflow(Consumer<DockerUniverseWorkflow> action) {

        WorkflowContext<UniverseParams, UniverseFixture> context = WorkflowContext
                .<UniverseParams, UniverseFixture>builder()
                .config(config)
                .fixture(new UniverseFixture())
                .build();
        DockerUniverseWorkflow wf = DockerUniverseWorkflow.builder()
                .context(context)
                .build();

        wf.init();
        try {
            action.accept(wf);
        } finally {
            wf.getContext().getUniverse().shutdown();
        }

        return wf;
    }

    /**
     * Runs a vm workflow
     *
     * @param action executes workflow logic, like init, deploy etc
     * @return universe workflow
     */
    public VmUniverseWorkflow vmWorkflow(Consumer<VmUniverseWorkflow> action) {

        WorkflowContext<VmFixtureContext, VmUniverseFixture> context = WorkflowContext
                .<VmFixtureContext, VmUniverseFixture>builder()
                .config(config)
                .fixture(new VmUniverseFixture())
                .build();

        VmUniverseWorkflow wf = VmUniverseWorkflow.builder().context(context).build();

        wf.init();
        try {
            action.accept(wf);
        } finally {
            wf.getContext().getUniverse().shutdown();
        }

        return wf;
    }

    /**
     * Runs a process workflow
     *
     * @param action executes workflow logic, like init, deploy etc
     * @return universe workflow
     */
    public ProcessUniverseWorkflow processWorkflow(Consumer<ProcessUniverseWorkflow> action) {

        WorkflowContext<UniverseParams, UniverseFixture> context = WorkflowContext
                .<UniverseParams, UniverseFixture>builder()
                .config(config)
                .fixture(new UniverseFixture())
                .build();
        ProcessUniverseWorkflow wf = ProcessUniverseWorkflow.builder()
                .context(context)
                .build();

        wf.init();
        try {
            action.accept(wf);
        } finally {
            wf.getContext().getUniverse().shutdown();
        }

        return wf;
    }
}
