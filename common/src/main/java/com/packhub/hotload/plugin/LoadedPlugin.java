package com.packhub.hotload.plugin;

import java.net.URLClassLoader;
import java.nio.file.Path;

/** 一个已经被加载进 JVM 的插件。 */
public final class LoadedPlugin {
    private final PluginManifest manifest;
    private final Path jar;
    private final URLClassLoader classLoader;
    private final PackHubPlugin instance;
    private volatile String lastError = "";
    private volatile int tickErrors;

    LoadedPlugin(PluginManifest manifest, Path jar, URLClassLoader classLoader, PackHubPlugin instance) {
        this.manifest = manifest;
        this.jar = jar;
        this.classLoader = classLoader;
        this.instance = instance;
    }

    public PluginManifest manifest() {
        return manifest;
    }

    public Path jar() {
        return jar;
    }

    public URLClassLoader classLoader() {
        return classLoader;
    }

    public PackHubPlugin instance() {
        return instance;
    }

    public String lastError() {
        return lastError;
    }

    void lastError(String value) {
        this.lastError = value == null ? "" : value;
    }

    public int tickErrors() {
        return tickErrors;
    }

    void incrementTickErrors() {
        this.tickErrors++;
    }

    public String statusLine() {
        try {
            String line = instance.statusLine();
            return line == null || line.isBlank() ? "" : line;
        } catch (Throwable t) {
            lastError(t.toString());
            return "";
        }
    }

    public String describe() {
        String base = manifest.displayName() + " " + manifest.version;
        return lastError.isBlank() ? base : base + "  (" + lastError + ")";
    }
}
