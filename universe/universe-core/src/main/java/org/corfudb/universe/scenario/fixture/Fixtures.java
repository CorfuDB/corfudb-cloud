package org.corfudb.universe.scenario.fixture;

import java.time.Duration;

/**
 * Fixture factory provides predefined fixtures
 */
public interface Fixtures {

    /**
     * Common constants used for test
     */
    class TestFixtureConst {
        // Default name of the CorfuTable created by CorfuClient
        public static final String DEFAULT_STREAM_NAME = "stream";

        // Default number of values written into CorfuTable
        public static final int DEFAULT_TABLE_ITER = 100;

        // Default number of times to poll layout
        public static final int DEFAULT_WAIT_POLL_ITER = 300;

        // Default time to wait before next layout poll: 1 second
        public static final Duration DEFAULT_WAIT_TIME = Duration.ofSeconds(1);

        private TestFixtureConst() {
            // prevent instantiation of this class
        }
    }

}
