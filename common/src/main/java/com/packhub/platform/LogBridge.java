package com.packhub.platform;

import com.packhub.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 把 common 里的极简日志门面接到加载器的 slf4j 上。 */
public final class LogBridge {
    private static boolean installed;

    private LogBridge() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        Logger logger = LoggerFactory.getLogger("PackHub");
        Log.setSink((level, message, error) -> {
            switch (level) {
                case DEBUG -> logger.debug(message);
                case INFO -> logger.info(message);
                case WARN -> logger.warn(message);
                case ERROR -> {
                    if (error != null) {
                        logger.error(message, error);
                    } else {
                        logger.error(message);
                    }
                }
            }
        });
    }
}
