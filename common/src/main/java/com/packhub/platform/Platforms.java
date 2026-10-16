package com.packhub.platform;

import java.util.ServiceLoader;

/**
 * 平台实现的注册点。加载器模块会主动调用 {@link #set(Platform)}，
 * 同时保留 {@link ServiceLoader} 兜底（META-INF/services）。
 */
public final class Platforms {
    private static volatile Platform instance;

    private Platforms() {
    }

    public static synchronized void set(Platform platform) {
        instance = platform;
    }

    public static Platform get() {
        Platform local = instance;
        if (local != null) {
            return local;
        }
        synchronized (Platforms.class) {
            if (instance == null) {
                instance = ServiceLoader.load(Platform.class).findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "未找到 PackHub 平台实现，请确认 Fabric/NeoForge 模块被打包进同一个 jar"));
            }
            return instance;
        }
    }
}
