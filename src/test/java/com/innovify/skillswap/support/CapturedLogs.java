package com.innovify.skillswap.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Collects what a class writes to the log during a test, so the tests can check the messages and their level. */
public final class CapturedLogs implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private CapturedLogs(Class<?> source) {
        logger = (Logger) LoggerFactory.getLogger(source);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    /** Starts capturing the log of the class; use it in a try-with-resources block. */
    public static CapturedLogs of(Class<?> source) {
        return new CapturedLogs(source);
    }

    /** The formatted messages (the arguments already replaced) written at the level, oldest first. */
    public List<String> messages(Level level) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /** Every event, to check the throwable or the level of a message. */
    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }
}
