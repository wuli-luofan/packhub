package org.slf4j;
public interface Logger {
    void debug(String message);
    void debug(String format, Object... args);
    void debug(String message, Throwable error);
    void info(String message);
    void info(String format, Object... args);
    void info(String message, Throwable error);
    void warn(String message);
    void warn(String format, Object... args);
    void warn(String message, Throwable error);
    void error(String message);
    void error(String format, Object... args);
    void error(String message, Throwable error);
}