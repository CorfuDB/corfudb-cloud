package org.corfudb.test;

import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.UniverseConfigurator;
import org.corfudb.universe.test.log.TestLogHelper;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.fail;

public abstract class AbstractCorfuUniverseTest {
    protected final CorfuUniverseTestRunner testRunner = new CorfuUniverseTestRunner();

    /**
     * testSetUp method is used for setting up configurations before each test.
     * It will be called before each test and register the test with MDC, so
     * that each test can log into different files.
     */
    @BeforeEach
    public void testSetUp() {
        TestLogHelper.startTestLogging(getClass());
    }

    /**
     * testCleanUp method is used to do clean up after each test executed.
     * It will be called after each test and remove the test from MDC.
     */
    @AfterEach
    public void testCleanUp() {
        TestLogHelper.stopTestLogging();
    }

    public static class CorfuUniverseTestRunner {
        private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();
        private final UniverseManager universeManager = configurator.universeManager;

        /**
         * executeTest method is used for executing different tests with given workflows.
         * This method will set up VM environment and run the test.
         * @param test The test function need to be executed.
         */
        public void executeTest(TestAction test) {
            universeManager.workflow(wf -> {
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

        @FunctionalInterface
        public interface TestAction {
            void execute(UniverseWorkflow<Fixture<UniverseParams>> wf) throws Exception;
        }
    }
}
