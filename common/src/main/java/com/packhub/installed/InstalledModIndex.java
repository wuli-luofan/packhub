package com.packhub.installed;

import com.packhub.platform.Platform;
import com.packhub.util.Log;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 扫描本地 mods/ 目录，读取每个 jar 的模组 id / 版本，
 * 用于「已安装」列表和依赖去重。
 */
public final class InstalledModIndex {

    public static final class Entry {
        public final Path path;
        public final String modId;
        public final String name;
        public final String version;
        public final String loader;
        public final boolean plugin;

        Entry(Path path, String modId, String name, String version, String loader, boolean plugin) {
            this.path = path;
            this.modId = modId;
            this.name = name;
            this.version = version;
            this.loader = loader;
            this.plugin = plugin;
        }

        public String displayName() {
            return name != null && !name.isBlank() ? name : (modId != null && !modId.isBlank() ? modId : path.getFileName().toString());
        }
    }

    private InstalledModIndex() {
    }

    /** 读取 mods/ 与 mods-disabled/ 下的所有 jar。 */
    public static List<Entry> scan(Platform platform) {
        List<Entry> entries = new ArrayList<>();
        collect(platform.modsDir(), entries);
        Path disabled = platform.gameDir().resolve("mods-disabled");
        if (Files.isDirectory(disabled)) {
            collect(disabled, entries);
        }
        return entries;
    }

    public static List<Path> installedJars(Platform platform) {
        List<Path> jars = new ArrayList<>();
        Path mods = platform.modsDir();
        if (!Files.isDirectory(mods)) {
            return jars;
        }
        try (var stream = Files.list(mods)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .forEach(jars::add);
        } catch (Exception e) {
            Log.warn("[PackHub] 扫描 mods 目录失败: %s", e.toString());
        }
        return jars;
    }

    private static void collect(Path dir, List<Entry> out) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            for (Path jar : stream.toList()) {
                String lower = jar.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".jar") && !lower.endsWith(".jar.disabled")) {
                    continue;
                }
                out.add(read(jar));
            }
        } catch (Exception e) {
            Log.warn("[PackHub] 读取 %s 失败: %s", dir, e.toString());
        }
    }

    public static Entry read(Path jar) {
        String modId = null;
        String name = null;
        String version = null;
        String loader = null;
        boolean plugin = false;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry fabric = zip.getEntry("fabric.mod.json");
            ZipEntry neo = zip.getEntry("META-INF/neoforge.mods.toml");
            if (neo == null) {
                neo = zip.getEntry("META-INF/mods.toml");
            }
            ZipEntry pluginEntry = zip.getEntry("packhub-plugin.json");
            if (fabric != null) {
                String json = readEntry(zip, fabric);
                modId = Json.peek(json, "id");
                name = Json.peek(json, "name");
                version = Json.peek(json, "version");
                loader = "fabric";
            } else if (neo != null) {
                String toml = readEntry(zip, neo);
                modId = Toml.peek(toml, "modId");
                name = Toml.peek(toml, "displayName");
                version = Toml.peek(toml, "version");
                loader = "neoforge";
            } else if (pluginEntry != null) {
                String json = readEntry(zip, pluginEntry);
                modId = Json.peek(json, "id");
                name = Json.peek(json, "name");
                version = Json.peek(json, "version");
                loader = "packhub-plugin";
                plugin = true;
            }
        } catch (Exception e) {
            Log.debug("[PackHub] 解析 %s 失败: %s", jar.getFileName(), e.toString());
        }
        if (modId == null) {
            modId = jar.getFileName().toString().replaceAll("(?i)\\.jar(\\.disabled)?$", "");
        }
        return new Entry(jar, modId, name, version, loader == null ? "unknown" : loader, plugin);
    }

    /** 已安装的模组 id 集合（小写）。 */
    public static List<String> installedModIds(Platform platform) {
        List<String> ids = new ArrayList<>();
        for (Entry entry : scan(platform)) {
            if (entry.modId != null) {
                ids.add(entry.modId.toLowerCase(Locale.ROOT));
            }
        }
        return ids;
    }

    private static String readEntry(ZipFile zip, ZipEntry entry) throws Exception {
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 极简 JSON 取值：只要第一个匹配的 "key": "value"，够用且不引入依赖。 */
    static final class Json {
        static String peek(String json, String key) {
            if (json == null) {
                return null;
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                    .matcher(json);
            return matcher.find() ? matcher.group(1) : null;
        }
    }

    /** 极简 TOML 取值：modId="x" / displayName="y"。 */
    static final class Toml {
        static String peek(String toml, String key) {
            if (toml == null) {
                return null;
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?m)^\\s*" + java.util.regex.Pattern.quote(key) + "\\s*=\\s*\"([^\"]*)\"")
                    .matcher(toml);
            return matcher.find() ? matcher.group(1) : null;
        }
    }
}
