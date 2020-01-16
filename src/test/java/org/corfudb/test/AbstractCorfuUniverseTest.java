package org.corfudb.test;

import org.corfudb.universe.test.log.TestLogHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class AbstractCorfuUniverseTest {
    Class className;

    @BeforeEach
    public void testSetUp() {
        TestLogHelper.startTestLogging(className);
    }

    @AfterEach
    public void testCleanUp() {
        TestLogHelper.stopTestLogging();
    }
}
