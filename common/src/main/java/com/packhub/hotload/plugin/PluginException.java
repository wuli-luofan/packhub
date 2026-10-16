package com.packhub.hotload.plugin;

/** 插件加载失败。 */
public class PluginException extends Exception {
    public PluginException(String message) {
        super(message);
    }

    public PluginException(String message, Throwable cause) {
        super(message, cause);
    }
}
