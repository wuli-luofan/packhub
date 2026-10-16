package com.packhub.hotload.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.packhub.config.PackHubConfig;
import com.packhub.platform.Platform;
import com.packhub.ui.Toasts;
import com.packhub.util.Log;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 真正的运行时模组热加载器：为每个插件建立独立的 {@link URLClassLoader}，
 * 支持加载、卸载（关闭类加载器）、重新加载。
 * <p>限制说明：受 JVM 与 Mixin 的约束，只有 PackHub 插件格式的 jar 能以这种方式热加载；
 * 普通 Fabric / NeoForge 模组必须重启进程（PackHub 提供一键软重启）。</p>
 */
public final class PluginRuntime {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final int API_VERSION = 1;

    private final Platform platform;
    private final PackHubConfig config;
    private final Path pluginDir;
    private final Path dataRoot;
    private final Map<String, LoadedPlugin> plugins = new LinkedHashMap<>();

    public PluginRuntime(Platform platform, PackHubConfig config) {
        this.platform = platform;
        this.config = config;
        this.pluginDir = config.file() != null
                ? config.file().getParent().resolve("plugins")
                : platform.configDir().resolve("packhub").resolve("plugins");
        this.dataRoot = pluginDir.resolve("data");
        ensureDirectories();
    }

    public Path pluginDir() {
        return pluginDir;
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(pluginDir);
            Files.createDirectories(dataRoot);
        } catch (Exception e) {
            Log.warn("[PackHub] 创建插件目录失败: %s", e.toString());
        }
    }

    public List<LoadedPlugin> plugins() {
        return new ArrayList<>(plugins.values());
    }

    public LoadedPlugin byId(String id) {
        return plugins.get(id.toLowerCase(Locale.ROOT));
    }

    /** 扫描插件目录并加载全部插件，返回成功数量。 */
    public int loadAll() {
        if (!config.enablePluginHotLoad) {
            return 0;
        }
        int count = 0;
        try (var stream = Files.list(pluginDir)) {
            for (Path jar : stream.toList()) {
                if (!jar.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    continue;
                }
                try {
                    load(jar);
                    count++;
                } catch (PluginException e) {
                    Log.warn("[PackHub] 加载插件 %s 失败: %s", jar.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.warn("[PackHub] 扫描插件目录失败: %s", e.toString());
        }
        return count;
    }

    /** 加载（或重新加载）一个插件 jar。必须在客户端线程调用。 */
    public LoadedPlugin load(Path jar) throws PluginException {
        if (!config.enablePluginHotLoad) {
            throw new PluginException("配置里已关闭插件热加载");
        }
        PluginManifest manifest = readManifest(jar);
        if (!manifest.valid()) {
            throw new PluginException("无效的 packhub-plugin.json（缺少 id 或 main）: " + jar.getFileName());
        }
        if (manifest.apiVersion > API_VERSION) {
            throw new PluginException("插件 " + manifest.id + " 需要更新的 PackHub API ("
                    + manifest.apiVersion + " > " + API_VERSION + ")");
        }
        String id = manifest.id.toLowerCase(Locale.ROOT);
        LoadedPlugin existing = plugins.get(id);
        if (existing != null) {
            unload(id);
        }

        URLClassLoader loader = null;
        try {
            URL url = jar.toUri().toURL();
            loader = new URLClassLoader(new URL[]{url}, PackHubPlugin.class.getClassLoader());
            Class<?> mainClass = Class.forName(manifest.main, true, loader);
            if (!PackHubPlugin.class.isAssignableFrom(mainClass)) {
                throw new PluginException(manifest.main + " 没有实现 PackHubPlugin 接口");
            }
            PackHubPlugin instance = (PackHubPlugin) mainClass.getDeclaredConstructor().newInstance();
            LoadedPlugin loadedPlugin = new LoadedPlugin(manifest, jar, loader, instance);
            PluginContext context = new ContextImpl(manifest);
            instance.onLoad(context);
            plugins.put(id, loadedPlugin);
            Log.info("[PackHub] 插件已加载: %s (%s)", manifest.displayName(), jar.getFileName());
            return loadedPlugin;
        } catch (PluginException e) {
            closeQuietly(loader);
            throw e;
        } catch (Throwable t) {
            closeQuietly(loader);
            throw new PluginException("加载插件 " + manifest.id + " 失败: " + t, t);
        }
    }

    /** 卸载插件：调用 onUnload 并关闭类加载器。 */
    public boolean unload(String id) {
        LoadedPlugin plugin = plugins.remove(id.toLowerCase(Locale.ROOT));
        if (plugin == null) {
            return false;
        }
        try {
            plugin.instance().onUnload();
        } catch (Throwable t) {
            Log.warn("[PackHub] 插件 %s 卸载回调异常: %s", plugin.manifest().id, t.toString());
        }
        closeQuietly(plugin.classLoader());
        Log.info("[PackHub] 插件已卸载: %s", plugin.manifest().id);
        return true;
    }

    public LoadedPlugin reload(String id) throws PluginException {
        LoadedPlugin plugin = byId(id);
        if (plugin == null) {
            throw new PluginException("插件未加载: " + id);
        }
        Path jar = plugin.jar();
        unload(id);
        return load(jar);
    }

    public void unloadAll() {
        for (String id : new ArrayList<>(plugins.keySet())) {
            unload(id);
        }
    }

    public void tickAll() {
        for (LoadedPlugin plugin : plugins()) {
            try {
                plugin.instance().onClientTick();
            } catch (Throwable t) {
                plugin.incrementTickErrors();
                plugin.lastError(t.toString());
                Log.warn("[PackHub] 插件 %s tick 异常: %s", plugin.manifest().id, t.toString());
            }
        }
    }

    public void resourceReloadAll() {
        for (LoadedPlugin plugin : plugins()) {
            try {
                plugin.instance().onResourceReload();
            } catch (Throwable t) {
                plugin.lastError(t.toString());
                Log.warn("[PackHub] 插件 %s 资源重载异常: %s", plugin.manifest().id, t.toString());
            }
        }
    }

    public PluginRuntimeState state() {
        PluginRuntimeState state = new PluginRuntimeState();
        state.enabled = config.enablePluginHotLoad;
        state.pluginDir = pluginDir;
        state.apiVersion = API_VERSION;
        state.loaded = plugins();
        return state;
    }

    // ------------------------------------------------------------ 内部

    private PluginManifest readManifest(Path jar) throws PluginException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("packhub-plugin.json");
            if (entry == null) {
                throw new PluginException("jar 内没有 packhub-plugin.json: " + jar.getFileName());
            }
            try (InputStream in = zip.getInputStream(entry);
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                PluginManifest manifest = GSON.fromJson(reader, PluginManifest.class);
                if (manifest == null) {
                    throw new PluginException("packhub-plugin.json 解析为空");
                }
                return manifest;
            }
        } catch (PluginException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginException("读取插件描述失败: " + e, e);
        }
    }

    private static void closeQuietly(URLClassLoader loader) {
        if (loader == null) {
            return;
        }
        try {
            loader.close();
        } catch (Exception ignored) {
            // 忽略
        }
    }

    /** 插件上下文实现。 */
    private final class ContextImpl implements PluginContext {
        private final PluginManifest manifest;

        ContextImpl(PluginManifest manifest) {
            this.manifest = manifest;
        }

        @Override
        public String pluginId() {
            return manifest.id;
        }

        @Override
        public String pluginName() {
            return manifest.displayName();
        }

        @Override
        public String pluginVersion() {
            return manifest.version;
        }

        @Override
        public Path dataDir() {
            Path dir = dataRoot.resolve(manifest.id);
            try {
                Files.createDirectories(dir);
            } catch (Exception e) {
                Log.warn("[PackHub] 创建插件数据目录失败: %s", e.toString());
            }
            return dir;
        }

        @Override
        public Path gameDir() {
            return platform.gameDir();
        }

        @Override
        public Path configDir() {
            return platform.configDir();
        }

        @Override
        public Path modsDir() {
            return platform.modsDir();
        }

        @Override
        public Object minecraftClient() {
            return Minecraft.getInstance();
        }

        @Override
        public void log(String message, Object... args) {
            Log.info("[" + manifest.id + "] " + message, args);
        }

        @Override
        public void toast(String message) {
            Toasts.show(manifest.displayName(), message);
        }
    }
}
