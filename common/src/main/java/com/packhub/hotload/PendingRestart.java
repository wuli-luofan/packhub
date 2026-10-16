package com.packhub.hotload;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.packhub.util.Log;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 记录「已安装但需要重启才生效」的模组。
 * 标准 Fabric / NeoForge 模组受 JVM 与 Mixin 限制无法在进程内热加载，
 * 因此这里把它们排进待重启队列，由软重启流程统一生效。
 */
public final class PendingRestart {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Set<String> pending = new LinkedHashSet<>();

    /**
     * @param file 队列的落盘位置（不依赖 Minecraft，方便脱离游戏做单元测试）
     */
    public PendingRestart(Path file) {
        this.file = file != null ? file : Path.of("pending-restart.json");
        load();
    }

    public synchronized void add(String fileName) {
        if (fileName != null && !fileName.isBlank() && pending.add(fileName)) {
            save();
        }
    }

    public synchronized void remove(String fileName) {
        if (pending.remove(fileName)) {
            save();
        }
    }

    public synchronized void clear() {
        if (!pending.isEmpty()) {
            pending.clear();
            save();
        }
    }

    public synchronized List<String> list() {
        return new ArrayList<>(pending);
    }

    public synchronized boolean isEmpty() {
        return pending.isEmpty();
    }

    public synchronized int size() {
        return pending.size();
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<String> stored = GSON.fromJson(reader, new TypeToken<List<String>>() {
            }.getType());
            if (stored != null) {
                pending.addAll(stored);
            }
        } catch (Exception e) {
            Log.warn("[PackHub] 读取待重启列表失败: %s", e.toString());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(pending), writer);
            }
        } catch (Exception e) {
            Log.warn("[PackHub] 保存待重启列表失败: %s", e.toString());
        }
    }
}
