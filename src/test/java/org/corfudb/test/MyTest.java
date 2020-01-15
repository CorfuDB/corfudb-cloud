package org.corfudb.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

@Slf4j
public class MyTest {
    @BeforeAll
    public static void testSetUp() {
        TestLogHelper.startTestLogging("MyTest");
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

