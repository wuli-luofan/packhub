package com.packhub.hotload.plugin;

import java.nio.file.Path;
import java.util.List;

/** 插件运行状态的只读快照，供界面显示。 */
public final class PluginRuntimeState {
    public boolean enabled;
    public int apiVersion;
    public Path pluginDir;
    public List<LoadedPlugin> loaded = List.of();
}
