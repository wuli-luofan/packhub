package com.packhub;

import com.packhub.config.PackHubConfig;
import com.packhub.curseforge.CurseForgeClient;
import com.packhub.curseforge.CurseForgeSettings;
import com.packhub.download.DownloadManager;
import com.packhub.hotload.HotLoadManager;
import com.packhub.modrinth.ModrinthClient;
import com.packhub.modrinth.ModrinthSettings;
import com.packhub.platform.Platform;
import com.packhub.platform.Platforms;
import com.packhub.ui.HubScreen;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PackHub 的公共内核：与加载器无关的逻辑都放在这里。
 * Fabric / NeoForge 两个模块各自只负责注入 {@link Platform} 实现并挂接事件。
 */
public final class PackHub {
    public static final String MOD_ID = "packhub";
    public static final String MOD_NAME = "PackHub";
    public static final String VERSION = "1.0.0";
    /** 目标 Minecraft 版本。 */
    public static final String MINECRAFT_VERSION = "26.3";

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static volatile boolean initialized;
    private static Platform platform;
    private static PackHubConfig config;
    private static ModrinthClient modrinth;
    private static CurseForgeClient curseForge;
    private static DownloadManager downloads;
    private static HotLoadManager hotLoad;

    private PackHub() {
    }

    /** 由各加载器模块在客户端初始化时调用一次。 */
    public static synchronized void init(Platform platformImpl) {
        if (initialized) {
            return;
        }
        initialized = true;
        platform = platformImpl;
        Platforms.set(platformImpl);

        config = PackHubConfig.load(platformImpl.configDir());
        modrinth = new ModrinthClient(modrinthSettings(config));
        curseForge = new CurseForgeClient(curseForgeSettings(config));
        downloads = new DownloadManager(platformImpl, config, modrinth, curseForge);
        hotLoad = new HotLoadManager(platformImpl, config);
        hotLoad.start();

        LOGGER.info("[PackHub] ready on {} {} (Minecraft {}, hot-load={})",
                platformImpl.loaderName(), platformImpl.loaderVersion(), MINECRAFT_VERSION,
                config.enablePluginHotLoad);

        // 退出时释放线程池与插件类加载器
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(PackHub::shutdown, "PackHub-Shutdown"));
        } catch (Throwable t) {
            LOGGER.debug("[PackHub] 注册关闭钩子失败", t);
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static Logger logger() {
        return LOGGER;
    }

    public static Platform platform() {
        return platform != null ? platform : Platforms.get();
    }

    public static PackHubConfig config() {
        if (config == null) {
            throw new IllegalStateException("PackHub 尚未初始化");
        }
        return config;
    }

    public static ModrinthClient modrinth() {
        return modrinth;
    }

    public static CurseForgeClient curseForge() {
        return curseForge;
    }

    /** 把配置里的网络参数同步进两个客户端（设置界面改动后调用）。 */
    public static void syncSettings() {
        if (config == null) {
            return;
        }
        if (modrinth != null) {
            ModrinthSettings target = modrinth.settings();
            target.apiBase = config.apiBase;
            target.apiBaseFallback = config.apiBaseFallback;
            target.useFallbackWhenPrimaryFails = config.useFallbackWhenPrimaryFails;
            target.cdnBaseOverride = config.cdnBaseOverride;
            target.connectTimeoutSeconds = config.connectTimeoutSeconds;
            target.readTimeoutSeconds = config.readTimeoutSeconds;
            target.downloadRetries = config.downloadRetries;
        }
        if (curseForge != null) {
            CurseForgeSettings target = curseForge.settings();
            target.apiBase = config.curseForgeApiBase;
            target.apiKey = config.curseForgeApiKey;
            target.fallbackDownloadBase = config.curseForgeFallbackDownloadBase;
            target.cdnBaseOverride = config.curseForgeCdnOverride;
            target.connectTimeoutSeconds = config.connectTimeoutSeconds;
            target.readTimeoutSeconds = config.readTimeoutSeconds;
            target.downloadRetries = config.downloadRetries;
        }
    }

    private static ModrinthSettings modrinthSettings(PackHubConfig config) {
        ModrinthSettings settings = new ModrinthSettings();
        settings.apiBase = config.apiBase;
        settings.apiBaseFallback = config.apiBaseFallback;
        settings.useFallbackWhenPrimaryFails = config.useFallbackWhenPrimaryFails;
        settings.cdnBaseOverride = config.cdnBaseOverride;
        settings.connectTimeoutSeconds = config.connectTimeoutSeconds;
        settings.readTimeoutSeconds = config.readTimeoutSeconds;
        settings.downloadRetries = config.downloadRetries;
        return settings;
    }

    private static CurseForgeSettings curseForgeSettings(PackHubConfig config) {
        CurseForgeSettings settings = new CurseForgeSettings();
        settings.apiBase = config.curseForgeApiBase;
        settings.apiKey = config.curseForgeApiKey;
        settings.fallbackDownloadBase = config.curseForgeFallbackDownloadBase;
        settings.cdnBaseOverride = config.curseForgeCdnOverride;
        settings.connectTimeoutSeconds = config.connectTimeoutSeconds;
        settings.readTimeoutSeconds = config.readTimeoutSeconds;
        settings.downloadRetries = config.downloadRetries;
        return settings;
    }

    public static DownloadManager downloads() {
        return downloads;
    }

    public static HotLoadManager hotLoad() {
        return hotLoad;
    }

    public static void openHub() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.gui.setScreen(new HubScreen(null)));
    }

    /** 客户端每 tick 调用（由加载器模块挂接）。 */
    public static void clientTick() {
        if (!initialized) {
            return;
        }
        hotLoad.tick();
        downloads.tick();
    }

    /** 游戏退出时释放线程与插件类加载器。 */
    public static void shutdown() {
        if (!initialized) {
            return;
        }
        try {
            hotLoad.stop();
        } catch (Throwable t) {
            LOGGER.warn("[PackHub] 关闭热加载器失败", t);
        }
        try {
            downloads.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("[PackHub] 关闭下载器失败", t);
        }
    }
}
