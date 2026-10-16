package com.packhub.hotload;

import com.packhub.config.PackHubConfig;
import com.packhub.hotload.plugin.LoadedPlugin;
import com.packhub.hotload.plugin.PluginException;
import com.packhub.hotload.plugin.PluginRuntime;
import com.packhub.modrinth.ContentType;
import com.packhub.platform.Platform;
import com.packhub.ui.Toasts;
import com.packhub.util.Log;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 热加载总调度：
 * <ul>
 *     <li>资源包 / 光影 / 数据包 —— 真正在运行时生效（原版重载 + Iris 反射调用）；</li>
 *     <li>PackHub 插件 jar —— 真正在运行时加载 / 卸载 / 重载；</li>
 *     <li>普通 Fabric / NeoForge 模组 —— 记入待重启队列，支持一键软重启完成“热加载”。</li>
 * </ul>
 */
public class HotLoadManager {

    private final Platform platform;
    private final PackHubConfig config;
    private final PendingRestart pendingRestart;
    private final PluginRuntime pluginRuntime;
    private final ModsWatcher watcher;
    private final int resourceReloadDelayTicks = 20;
    private int reloadCountdown = -1;

    public HotLoadManager(Platform platform, PackHubConfig config) {
        this.platform = platform;
        this.config = config;
        this.pendingRestart = new PendingRestart(config.file() != null
                ? config.file().getParent().resolve("pending-restart.json")
                : platform.configDir().resolve("packhub").resolve("pending-restart.json"));
        this.pluginRuntime = new PluginRuntime(platform, config);
        this.watcher = new ModsWatcher(List.of(platform.modsDir(), platform.gameDir().resolve("mods-disabled")));
    }

    public void start() {
        if (config.watchModsFolder) {
            watcher.start();
        }
        if (config.enablePluginHotLoad) {
            runOnClient(() -> {
                int loaded = pluginRuntime.loadAll();
                if (loaded > 0) {
                    Log.info("[PackHub] 启动时热加载了 %d 个插件", loaded);
                }
            });
        }
    }

    public void stop() {
        watcher.close();
        runOnClient(() -> pluginRuntime.unloadAll());
    }

    public void tick() {
        if (reloadCountdown > 0) {
            reloadCountdown--;
            if (reloadCountdown == 0) {
                reloadCountdown = -1;
                ResourcePackReloader.reload(config);
                pluginRuntime.resourceReloadAll();
            }
        }
        if (config.enablePluginHotLoad) {
            pluginRuntime.tickAll();
        }
    }

    public PluginRuntime plugins() {
        return pluginRuntime;
    }

    public PendingRestart pendingRestart() {
        return pendingRestart;
    }

    public ModsWatcher watcher() {
        return watcher;
    }

    public boolean hasShaderLoader() {
        return ResourcePackReloader.hasShaderLoader();
    }

    // ------------------------------------------------------------ 安装后处理

    /** 安装完成后的热加载策略。 */
    public HotLoadOutcome apply(ContentType type, Path installed, boolean enableAfterInstall) {
        switch (type) {
            case RESOURCEPACK: {
                if (!config.hotReloadAfterInstall) {
                    return HotLoadOutcome.skipped("已下载（未开启自动热重载）");
                }
                if (enableAfterInstall && config.autoEnableResourcePacks) {
                    ResourcePackReloader.enablePack(installed);
                }
                scheduleResourceReload();
                return HotLoadOutcome.reloaded("资源包已热重载并生效");
            }
            case SHADER: {
                if (!config.hotReloadAfterInstall) {
                    return HotLoadOutcome.skipped("已下载（未开启自动热重载）");
                }
                ResourcePackReloader.reloadShaderLoader();
                scheduleResourceReload();
                String extra = ResourcePackReloader.hasShaderLoader()
                        ? "光影已热重载，可在 Iris 光影列表中选择"
                        : "光影已放入 shaderpacks，安装 Iris/Oculus 后在列表中选择";
                return HotLoadOutcome.reloaded(extra);
            }
            case DATAPACK: {
                if (enableAfterInstall) {
                    Toasts.show("PackHub", "数据包已下载，使用 /datapack enable 或重载生效");
                }
                return HotLoadOutcome.skipped("数据包已下载");
            }
            case MOD:
            case MODPACK: {
                return applyMod(installed);
            }
            default:
                return HotLoadOutcome.skipped("已下载");
        }
    }

    private HotLoadOutcome applyMod(Path installed) {
        JarKind kind = ModJarInspector.inspect(installed);
        if (kind == JarKind.PACKHUB_PLUGIN && config.enablePluginHotLoad) {
            try {
                Files.createDirectories(pluginRuntime.pluginDir());
                Path pluginJar = pluginRuntime.pluginDir().resolve(installed.getFileName().toString());
                Files.copy(installed, pluginJar, StandardCopyOption.REPLACE_EXISTING);
                runOnClient(() -> {
                    try {
                        LoadedPlugin plugin = pluginRuntime.load(pluginJar);
                        Toasts.show("PackHub 热加载", "插件 " + plugin.manifest().displayName() + " 已在运行中生效");
                    } catch (PluginException e) {
                        Toasts.error("PackHub 热加载", "插件加载失败: " + e.getMessage());
                    }
                });
                return HotLoadOutcome.reloaded("插件已热加载，无需重启");
            } catch (Throwable t) {
                return HotLoadOutcome.skipped("插件已下载，但热加载失败: " + t.getMessage());
            }
        }

        // 普通模组：JVM 层面无法卸载，登记待重启
        pendingRestart.add(installed.getFileName().toString());
        if (config.allowSoftRestartForMods && config.autoSoftRestart) {
            boolean started = softRestart();
            return started
                    ? HotLoadOutcome.restartRequired("模组已下载，正在软重启以载入")
                    : HotLoadOutcome.restartRequired("模组已下载，请手动重启游戏");
        }
        String hint = switch (kind) {
            case FABRIC_MOD -> "Fabric 模组已下载，需重启（可在「热加载」页一键软重启）";
            case NEOFORGE_MOD -> "NeoForge 模组已下载，需重启（可在「热加载」页一键软重启）";
            default -> "已下载到 mods 目录，需重启生效";
        };
        return HotLoadOutcome.restartRequired(hint);
    }

    // ------------------------------------------------------------ 操作入口

    /** 延迟 1 秒（20 tick）重载资源，避免在下载线程里直接操作客户端状态。 */
    public void scheduleResourceReload() {
        reloadCountdown = resourceReloadDelayTicks;
    }

    public void reloadNow() {
        ResourcePackReloader.reload(config);
        pluginRuntime.resourceReloadAll();
    }

    /** 一键软重启（会在重启后自动载入新模组）。 */
    public boolean softRestart() {
        if (!GameRestarter.canRestart()) {
            Toasts.error("PackHub", "无法获取启动命令，已生成重启脚本到 config/packhub/");
            GameRestarter.writeRestartScript(platform.configDir().resolve("packhub"));
            return false;
        }
        boolean started = GameRestarter.restart();
        if (started) {
            Toasts.show("PackHub", "正在重启游戏以载入新模组…");
            runOnClient(() -> Minecraft.getInstance().stop());
        } else {
            Toasts.error("PackHub", "软重启失败，请手动重启游戏");
        }
        return started;
    }

    /** 把 mods/ 里的某个 jar 停用（移动到 mods-disabled/）。 */
    public boolean disableMod(Path jar) {
        try {
            Path disabledDir = platform.gameDir().resolve("mods-disabled");
            Files.createDirectories(disabledDir);
            Files.move(jar, disabledDir.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            pendingRestart.add(jar.getFileName().toString());
            watcher.markDirty();
            return true;
        } catch (Exception e) {
            Log.warn("[PackHub] 停用 %s 失败: %s", jar.getFileName(), e.toString());
            return false;
        }
    }

    /** 把 mods-disabled/ 里的某个 jar 重新启用。 */
    public boolean enableMod(Path jar) {
        try {
            Path mods = platform.modsDir();
            Files.createDirectories(mods);
            Files.move(jar, mods.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            pendingRestart.add(jar.getFileName().toString());
            watcher.markDirty();
            return true;
        } catch (Exception e) {
            Log.warn("[PackHub] 启用 %s 失败: %s", jar.getFileName(), e.toString());
            return false;
        }
    }

    public boolean deleteMod(Path jar) {
        try {
            Files.deleteIfExists(jar);
            pendingRestart.remove(jar.getFileName().toString());
            watcher.markDirty();
            return true;
        } catch (Exception e) {
            Log.warn("[PackHub] 删除 %s 失败: %s", jar.getFileName(), e.toString());
            return false;
        }
    }

    /** 已安装但还没生效的模组（重启后才会载入）。 */
    public List<String> pendingList() {
        return pendingRestart.list();
    }

    public String backendSummary() {
        String shader = ResourcePackReloader.hasShaderLoader() ? "Iris/Oculus 已就绪" : "未检测到光影加载器";
        return platform.loaderName() + " · " + shader + " · 插件 "
                + pluginRuntime.plugins().size() + " 个";
    }

    /** 在当前线程或客户端线程执行。 */
    public static void runOnClient(Runnable action) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.isSameThread()) {
                action.run();
            } else {
                minecraft.execute(action);
            }
        } catch (Throwable t) {
            try {
                action.run();
            } catch (Throwable inner) {
                Log.warn("[PackHub] 客户端任务执行失败: %s", inner.toString());
            }
        }
    }

    public static String describeKind(JarKind kind) {
        return switch (kind) {
            case PACKHUB_PLUGIN -> "可热加载插件";
            case FABRIC_MOD -> "Fabric 模组（需重启）";
            case NEOFORGE_MOD -> "NeoForge 模组（需重启）";
            case UNKNOWN -> "未知 jar";
        };
    }

    public static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
