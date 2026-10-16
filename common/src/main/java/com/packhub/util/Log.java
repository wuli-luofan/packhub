package com.packhub.util;

/**
 * 极简日志门面：核心逻辑不直接依赖 MC / slf4j，方便脱离游戏做单元与联网冒烟测试。
 * 加载器模块在初始化时把 {@link Sink} 接到 slf4j。
 */
public final class Log {
    public interface Sink {
        void log(Level level, String message, Throwable error);
    }

    public enum Level { DEBUG, INFO, WARN, ERROR }

    private static volatile Sink sink = (level, message, error) -> {
        System.err.println("[" + level + "] " + message);
        if (error != null) {
            error.printStackTrace(System.err);
        }
    };

    private Log() {
    }

    public static void setSink(Sink newSink) {
        if (newSink != null) {
            sink = newSink;
        }
    }

    public static void debug(String message, Object... args) {
        emit(Level.DEBUG, message, args, null);
    }

    public static void info(String message, Object... args) {
        emit(Level.INFO, message, args, null);
    }

    public static void warn(String message, Object... args) {
        emit(Level.WARN, message, args, null);
    }

    public static void error(String message, Throwable error, Object... args) {
        emit(Level.ERROR, message, args, error);
    }

    private static void emit(Level level, String message, Object[] args, Throwable error) {
        String rendered = message;
        if (args != null && args.length > 0) {
            try {
                rendered = String.format(message, args);
            } catch (RuntimeException ignored) {
                rendered = message + " " + java.util.Arrays.toString(args);
            }
        }
        sink.log(level, rendered, error);
    }
}
