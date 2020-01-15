package org.corfudb.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Slf4j
public class MyTest {
    @BeforeAll
    public static void testSetUp() throws Exception {
        TestLogHelper.startTestLogging("Method");
    }

    @AfterAll
    public static void testCleanUp() {
        TestLogHelper.stopTestLogging();
    }

    @Test
    public void test1() {
        log.info("test1");
    }

    @Test
    public void test2() {
        log.error("test2");
    }
}

