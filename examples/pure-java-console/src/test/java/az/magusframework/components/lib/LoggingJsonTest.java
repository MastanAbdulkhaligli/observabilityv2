package az.magusframework.components.lib;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LoggingJsonTest {

    private static final Logger log = LoggerFactory.getLogger(LoggingJsonTest.class);

    @Test
    void emitsJsonLog() {
        log.info("hello from test");
        System.out.println("stdout from test");
    }
}