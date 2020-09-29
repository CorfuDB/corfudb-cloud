package org.corfudb.test;

import lombok.Builder;
import lombok.NonNull;
import org.corfudb.universe.api.UniverseManager;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.api.workflow.UniverseWorkflow.WorkflowConfig;
import org.corfudb.universe.infrastructure.docker.workflow.DockerUniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.scenario.fixture.Fixtures.UniverseFixture;
import org.corfudb.universe.test.UniverseConfigurator;
import org.corfudb.universe.test.log.TestLogHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import static org.assertj.core.api.Assertions.fail;
import static org.corfudb.universe.test.UniverseConfigurator.getServerVersion;

public abstract class AbstractCorfuUniverseTest {
    protected CorfuUniverseTestRunner testRunner;

    /**
     * testSetUp method is used for setting up configurations before each test.
     * It will be called before each test and register the test with MDC, so
     * that each test can log into different files.
     */
    @BeforeEach
    public void testSetUp(TestInfo testInfo) {
        TestLogHelper.startTestLogging(getClass());
        testRunner = CorfuUniverseTestRunner.builder()
                .testName(getClass().getSimpleName() + "#" + testInfo.getDisplayName())
                .build();
    }

    /**
     * testCleanUp method is used to do clean up after each test executed.
     * It will be called after each test and remove the test from MDC.
     */
    @AfterEach
    public void testCleanUp() {
        TestLogHelper.stopTestLogging();
    }

    @Builder
    public static class CorfuUniverseTestRunner {
        private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();
        @NonNull
        private final String testName;

        /**
         * executeTest method is used for executing different tests with given workflows.
         * This method will set up VM environment and run the test.
         *
         * @param test The test function need to be executed.
         */
        public void executeStatefulVmTest(TestAction test) {
            WorkflowConfig config = getConfig();

            UniverseManager.builder()
                    .config(config)
                    .build()
                    .vmWorkflow(wf -> {
                        wf.setup(configurator.vmSetup);
                        wf.setup(fixture -> {
                            //don't stop corfu cluster after the test
                            fixture.getUniverse().cleanUpEnabled(false);
                        });
                        wf.initUniverse();
                        try {
                            test.execute(wf);
                        } catch (Exception e) {
                            fail("Failed: ", e);
                        }
                    });
        }

        private WorkflowConfig getConfig() {
            return WorkflowConfig.builder()
                    .testName(testName)
                    .corfuServerVersion(getServerVersion())
                    .build();
        }

        /**
         * executeTest method is used for executing different tests with given workflows.
         * This method will set up docker environment and run the test.
         *
         * @param test The test function need to be executed.
         */
        public void executeDockerTest(TestAction<UniverseParams, UniverseFixture, DockerUniverseWorkflow> test) {
            WorkflowConfig config = getConfig();
            UniverseManager.builder()
                    .config(config)
                    .build()
                    .dockerWorkflow(wf -> {
                        wf.setup(configurator.dockerSetup);
                        wf.setup(fixture -> {
                            fixture.getCassandraCommonParams().enabled(true);
                            fixture.getMangleCommonParams().enabled(true);
                        });
                        wf.deploy();
                        try {
                            test.execute(wf);
                        } catch (Exception e) {
                            fail("Failed: ", e);
                        }
                        wf.getContext().getUniverse().shutdown();
                    });
        }

        @FunctionalInterface
        public interface TestAction<P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> {
            void execute(U wf) throws Exception;
        }
    }
}
