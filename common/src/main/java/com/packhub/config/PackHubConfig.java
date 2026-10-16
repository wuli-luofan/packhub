package com.packhub.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.packhub.PackHub;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PackHub 配置，存放在 config/packhub.json。
 * 字段保持 public 以便 Gson 直接读写（我们的类不会被混淆）。
 */
public final class PackHubConfig {

    /** 主 API 地址（Modrinth 官方）。 */
    public String apiBase = "https://api.modrinth.com/v2";
    /** 备用 API 镜像，主地址不可用时自动切换（中国大陆建议保持开启）。 */
    public String apiBaseFallback = "https://api.modrinth.com/v2";
    public boolean useFallbackWhenPrimaryFails = true;
    /** 下载直链是否需要替换域名（例如把 cdn.modrinth.com 换成镜像）。留空表示不改写。 */
    public String cdnBaseOverride = "";

    // ------------------------------------------------------------ CurseForge

    /** 默认搜索来源：modrinth / curseforge。 */
    public String preferredSource = "modrinth";
    /** 是否启用 CurseForge 来源。 */
    public boolean enableCurseForge = true;
    public String curseForgeApiBase = "https://api.curseforge.com/v1";
    /**
     * CurseForge API Key（在 https://console.curseforge.com/ 申请）。
     * PackHub 不内置任何 Key，请填自己的。
     */
    public String curseForgeApiKey = "";
    /** downloadUrl 缺失时按 fileId 拼兜底直链的前缀。 */
    public String curseForgeFallbackDownloadBase = "https://edge.forgecdn.net/files/";
    /** CurseForge CDN 镜像（例如国内加速），留空表示不改写。 */
    public String curseForgeCdnOverride = "";

    /** 同时进行的下载数量。 */
    public int maxConcurrentDownloads = 3;
    /** 单个下载的重试次数。 */
    public int downloadRetries = 3;
    /** 连接超时（秒）。 */
    public int connectTimeoutSeconds = 15;
    /** 读取超时（秒）。 */
    public int readTimeoutSeconds = 60;

    /** 安装模组时自动解析必选前置。 */
    public boolean autoResolveDependencies = true;
    /** 依赖解析的最大递归深度。 */
    public int maxDependencyDepth = 3;

    /** 安装资源包后自动勾选并立刻生效。 */
    public boolean autoEnableResourcePacks = true;
    /** 安装资源包/光影后立刻热重载。 */
    public boolean hotReloadAfterInstall = true;
    /** 光影热重载时尝试调用 Iris/Oculus（反射，装了就调）。 */
    public boolean reloadShaderLoader = true;

    /** 允许加载 PackHub 插件格式的热加载模组。 */
    public boolean enablePluginHotLoad = true;
    /** 标准模组（fabric.mod.json / neoforge.mods.toml）安装后提示软重启。 */
    public boolean allowSoftRestartForMods = true;
    /** 软重启：自动用同一命令行重启游戏进程。 */
    public boolean autoSoftRestart = false;
    /** 监控 mods/ 与 mods-disabled/ 目录变化。 */
    public boolean watchModsFolder = true;

    /** 显示 Toast 提示。 */
    public boolean showToasts = true;
    /** 允许中文搜索：把中文关键词翻译成英文再搜（Modrinth/CurseForge 的索引基本是英文）。 */
    public boolean enableChineseSearch = true;
    /** 中文搜索时最多额外发几条查询（1 = 只用翻译结果，2 = 翻译 + 单词兜底）。 */
    public int chineseSearchMaxQueries = 2;
    /** 开始安装时自动打开进度界面（可以在里面取消安装）。 */
    public boolean autoOpenProgressScreen = true;
    /** 安装完成后弹出「装了什么」的通知。 */
    public boolean showInstallSummary = true;

    // ------------------------------------------------------------ 联机同步

    /** 作为房主时，把 PackHub 装过的模组清单同步给其他玩家。 */
    public boolean enableHostModSync = true;
    /** 作为客户端时，允许接受房主下发的模组（联机自动下载）。 */
    public boolean allowServerModPush = true;
    /** 所有玩家下载完成后，房主端自动重启（默认关闭：先让房主确认再重启）。 */
    public boolean syncAutoRestart = false;
    /** 同步时是否也带上资源包 / 光影（默认只同步模组）。 */
    public boolean syncResourcePacks = false;
    /** 单个同步文件的大小上限（MB），超过就不下发，避免误推大整合包。 */
    public int syncMaxFileSizeMb = 200;
    /** 搜索结果每页数量。 */
    public int searchPageSize = 20;
    /** 是否过滤掉与当前加载器不兼容的模组。 */
    public boolean filterByCurrentLoader = true;
    /** 搜索结果排序：relevance / downloads / follows / newest / updated。 */
    public String searchIndex = "relevance";

    /** 界面语言：auto / zh_cn / en_us。 */
    public String language = "auto";
    /** 首次打开时是否展示提示。 */
    public boolean showFirstRunHint = true;

    // ---------------------------------------------------------------- 读写

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Path configFile;

    public static PackHubConfig load(Path configDir) {
        configFile = configDir.resolve("packhub.json");
        PackHubConfig config = new PackHubConfig();
        if (Files.isRegularFile(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                PackHubConfig loaded = GSON.fromJson(reader, PackHubConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (Exception e) {
                PackHub.logger().warn("[PackHub] 读取配置失败，使用默认配置", e);
            }
        }
        config.sanitize();
        config.save();
        return config;
    }

    public void sanitize() {
        if (apiBase == null || apiBase.isBlank()) {
            apiBase = "https://api.modrinth.com/v2";
        }
        while (apiBase.endsWith("/")) {
            apiBase = apiBase.substring(0, apiBase.length() - 1);
        }
        if (apiBaseFallback != null) {
            while (apiBaseFallback.endsWith("/")) {
                apiBaseFallback = apiBaseFallback.substring(0, apiBaseFallback.length() - 1);
            }
        }
        maxConcurrentDownloads = clamp(maxConcurrentDownloads, 1, 16);
        downloadRetries = clamp(downloadRetries, 0, 10);
        connectTimeoutSeconds = clamp(connectTimeoutSeconds, 3, 120);
        readTimeoutSeconds = clamp(readTimeoutSeconds, 5, 600);
        maxDependencyDepth = clamp(maxDependencyDepth, 0, 10);
        searchPageSize = clamp(searchPageSize, 5, 50);
        if (searchIndex == null || searchIndex.isBlank()) {
            searchIndex = "relevance";
        }
        if (preferredSource == null || preferredSource.isBlank()) {
            preferredSource = "modrinth";
        }
        preferredSource = preferredSource.trim().toLowerCase(java.util.Locale.ROOT);
        if (curseForgeApiBase == null || curseForgeApiBase.isBlank()) {
            curseForgeApiBase = "https://api.curseforge.com/v1";
        }
        while (curseForgeApiBase.endsWith("/")) {
            curseForgeApiBase = curseForgeApiBase.substring(0, curseForgeApiBase.length() - 1);
        }
        if (curseForgeApiKey == null) {
            curseForgeApiKey = "";
        }
        curseForgeApiKey = curseForgeApiKey.trim();
        if (curseForgeFallbackDownloadBase == null || curseForgeFallbackDownloadBase.isBlank()) {
            curseForgeFallbackDownloadBase = "https://edge.forgecdn.net/files/";
        }
        if (curseForgeCdnOverride == null) {
            curseForgeCdnOverride = "";
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public synchronized void save() {
        if (configFile == null) {
            return;
        }
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            PackHub.logger().warn("[PackHub] 保存配置失败", e);
        }
    }

    public Path file() {
        return configFile;
    }
}
