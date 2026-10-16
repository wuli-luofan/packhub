package com.example.packhub;

import com.packhub.hotload.plugin.PackHubPlugin;
import com.packhub.hotload.plugin.PluginContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * 示例热加载插件：不依赖 Minecraft，只用到 PackHub 的插件 API，
 * 因此可以在游戏运行中被加载 / 卸载 / 重载。
 */
public class ExamplePlugin implements PackHubPlugin {

    private PluginContext context;
    private int ticks;
    private int reloads;

    @Override
    public void onLoad(PluginContext context) throws Exception {
        this.context = context;
        Path dataDir = context.dataDir();
        Files.createDirectories(dataDir);
        Path log = dataDir.resolve("history.log");
        String line = "loaded at " + LocalDateTime.now() + System.lineSeparator();
        Files.writeString(log, line, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        context.log("示例插件已加载；数据目录 %s", dataDir);
        context.toast("示例插件已热加载");
    }

    @Override
    public void onClientTick() {
        ticks++;
        if (ticks % 1200 == 0) { // 约每分钟
            context.toast("示例插件运行中：已 " + (ticks / 20) + " 秒，资源重载 " + reloads + " 次");
        }
    }

    @Override
    public void onResourceReload() {
        reloads++;
        context.log("资源重载了（第 %d 次）", reloads);
    }

    @Override
    public void onUnload() {
        context.log("示例插件被卸载（或正在重载）");
    }

    @Override
    public String statusLine() {
        return "tick=" + ticks + " reload=" + reloads;
    }
}
