package org.corfudb.test;
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.UniverseConfigurator;
import org.corfudb.universe.test.log.TestLogHelper;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.fail;
public abstract class AbstractCorfuUniverseTest {
    protected final CorfuUniverseTestRunner testRunner = new CorfuUniverseTestRunner();
    @BeforeEach
    public void testSetUp() {
        TestLogHelper.startTestLogging(getClass());
    }
    @AfterEach
    public void testCleanUp() {
        TestLogHelper.stopTestLogging();
    }
    public static class CorfuUniverseTestRunner {
        private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();
        private final UniverseManager universeManager = configurator.universeManager;
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