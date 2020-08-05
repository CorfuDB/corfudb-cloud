package org.corfudb.test;

import lombok.Builder;
import lombok.NonNull;
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.UniverseConfigurator;
import org.corfudb.universe.test.log.TestLogHelper;
import org.corfudb.universe.universe.Universe.UniverseMode;
import org.corfudb.universe.universe.UniverseParams;
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
            UniverseManager.builder()
                    .testName(testName)
                    .universeMode(UniverseMode.VM)
                    .corfuServerVersion(getServerVersion())
                    .build()
                    .workflow(wf -> {
                        wf.setupVm(configurator.vmSetup);
                        wf.setupVm(fixture -> {
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

        /**
         * executeTest method is used for executing different tests with given workflows.
         * This method will set up docker environment and run the test.
         *
         * @param test The test function need to be executed.
         */
        public void executeDockerTest(TestAction test) {
            UniverseManager.builder()
                    .testName(testName)
                    .universeMode(UniverseMode.DOCKER)
                    .corfuServerVersion(getServerVersion())
                    .build()
                    .workflow(wf -> {
                        wf.setupDocker(configurator.dockerSetup);
                        wf.deploy();
                        try {
                            test.execute(wf);
                        } catch (Exception e) {
                            fail("Failed: ", e);
                        }
                        wf.shutdown();
                    });
        }

        @FunctionalInterface
        public interface TestAction {
            void execute(UniverseWorkflow<Fixture<UniverseParams>> wf) throws Exception;
        }
    }
}
