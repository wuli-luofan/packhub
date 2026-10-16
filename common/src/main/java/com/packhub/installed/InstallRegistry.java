package com.packhub.installed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.packhub.download.InstallResult;
import com.packhub.modrinth.ContentType;
import com.packhub.util.Log;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 安装记录：PackHub 装过的每一个文件（项目 / 版本 / 直链 / sha1 / 落盘位置）。
 *
 * <p>两个用途：</p>
 * <ol>
 *     <li>界面上「最近安装」与完成通知；</li>
 *     <li><b>联机同步的清单来源</b>——房主把自己的安装记录广播给其他玩家，
 *         对方据此自动下载同样的文件（见 {@code com.packhub.sync}）。</li>
 * </ol>
 *
 * <p>刻意不依赖 Minecraft，方便脱离游戏做单元测试。</p>
 */
public final class InstallRegistry {

    /** 一条安装记录。字段名会直接写进同步协议，改名要谨慎。 */
    public static final class Entry {
        public String source = "modrinth";
        public String projectId = "";
        public String slug = "";
        public String title = "";
        public String versionId = "";
        public String versionNumber = "";
        public String fileName = "";
        public String url = "";
        public String sha1 = "";
        public long size;
        public String type = "MOD";
        public String installedAt = "";

        public ContentType contentType() {
            try {
                return ContentType.valueOf(type);
            } catch (RuntimeException e) {
                return ContentType.MOD;
            }
        }

        /** 是否是我们能自动帮别人拿到的条目（要有可下载直链）。 */
        public boolean shareable() {
            return url != null && !url.isBlank() && fileName != null && !fileName.isBlank();
        }

        public String describe() {
            String version = versionNumber == null || versionNumber.isBlank() ? versionId : versionNumber;
            return (title == null || title.isBlank() ? fileName : title) + " " + version;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public InstallRegistry(Path file) {
        this.file = file;
        load();
    }

    public Path file() {
        return file;
    }

    /** 记录一次成功安装（同一个文件重复安装会覆盖旧记录）。 */
    public synchronized void record(InstallResult result) {
        if (result == null || result.file == null || result.path == null) {
            return;
        }
        Entry entry = new Entry();
        if (result.project != null) {
            entry.source = result.project.source.id();
            entry.projectId = result.project.projectId == null ? "" : result.project.projectId;
            entry.slug = result.project.slug == null ? "" : result.project.slug;
            entry.title = result.project.title == null ? "" : result.project.title;
        }
        if (result.version != null) {
            entry.versionId = result.version.id == null ? "" : result.version.id;
            entry.versionNumber = result.version.versionNumber == null ? "" : result.version.versionNumber;
        }
        entry.fileName = result.file.filename == null ? "" : result.file.filename;
        entry.url = result.file.url == null ? "" : result.file.url;
        entry.sha1 = result.file.sha1 == null ? "" : result.file.sha1;
        entry.size = result.file.size;
        entry.type = result.type == null ? ContentType.MOD.name() : result.type.name();
        entry.installedAt = Instant.now().toString();
        entries.put(entry.fileName, entry);
        save();
        Log.info("[PackHub] 记录安装: %s -> %s", entry.describe(), entry.fileName);
    }

    public synchronized void forget(String fileName) {
        if (entries.remove(fileName) != null) {
            save();
        }
    }

    public synchronized List<Entry> entries() {
        return new ArrayList<>(entries.values());
    }

    public synchronized Entry byFileName(String fileName) {
        return entries.get(fileName);
    }

    public synchronized int size() {
        return entries.size();
    }

    /**
     * 取「当前确实存在、且能分享给别人」的条目。
     *
     * @param modsDir 本机 mods 目录（用来确认文件还在，玩家手动删掉的就不该再下发）
     */
    public synchronized List<Entry> shareableMods(Path modsDir) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.contentType() != ContentType.MOD || !entry.shareable()) {
                continue;
            }
            if (modsDir != null && !Files.isRegularFile(modsDir.resolve(entry.fileName))) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    // ------------------------------------------------------------ 持久化

    private void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Entry> stored = GSON.fromJson(reader, new TypeToken<List<Entry>>() {
            }.getType());
            if (stored != null) {
                for (Entry entry : stored) {
                    if (entry != null && entry.fileName != null && !entry.fileName.isBlank()) {
                        entries.put(entry.fileName, entry);
                    }
                }
            }
        } catch (Exception e) {
            Log.warn("[PackHub] 读取安装记录失败: %s", e.toString());
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(entries.values()), writer);
            }
        } catch (Exception e) {
            Log.warn("[PackHub] 保存安装记录失败: %s", e.toString());
        }
    }
}
