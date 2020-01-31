package org.corfudb.universe.test.log;

import org.slf4j.MDC;

/**
 * Class to handle setting/removing MDC on per test case basis. This helps
 * us log each test case into it's own log file.
 * See reference @url http://www.nullin.com/2010/07/28/logging-tests-to-separate-files/
 */
public final class TestLogHelper {
    private static final String TEST_NAME = "testName";

    private TestLogHelper() {
        //prevent creating class util instances
    }

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
