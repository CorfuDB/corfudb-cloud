package org.corfudb.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

@Slf4j
public class SecondTest {
    @BeforeAll
    public static void testSetUp() {
        TestLogHelper.startTestLogging(SecondTest.class);
    }

    @AfterAll
    public static void testCleanUp() {
        TestLogHelper.stopTestLogging();
    }

    @Test
    public void test1() {
        log.info("hello world");
    }

    @Test
    public void test2() {
        log.error("succeed!");
    }
}

