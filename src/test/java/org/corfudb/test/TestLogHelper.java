package org.corfudb.test;

import org.slf4j.MDC;

/**
 * Class to handle setting/removing MDC on per test case basis. This helps
 * us log each test case into it's own log file.
 */
public class TestLogHelper {
    public static final String TEST_NAME = "testName";

    /**
     * Adds the test name to MDC so that sift appender can use it and log the new
     * log events to a different file
     *
     * @param name Class name of the test log
     */
    public static void startTestLogging(Class<?> name) {
        MDC.put(TEST_NAME, name.getCanonicalName());
    }

    /**
     * Removes the key (log file name) from MDC
     */
    public static void stopTestLogging() {
        MDC.remove(TEST_NAME);
    }
}