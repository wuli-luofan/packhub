package com.packhub.hotload;

import com.packhub.util.Log;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 监控 mods/ 与 mods-disabled/，这样玩家在外面手动丢 jar 进来时，
 * 界面上的「已安装 / 热加载」列表能自动刷新。
 */
public final class ModsWatcher implements AutoCloseable {

    private final List<Path> watched = new ArrayList<>();
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private WatchService watchService;
    private Thread thread;
    private volatile boolean running;

    public ModsWatcher(List<Path> directories) {
        this.watched.addAll(directories);
    }

    public void start() {
        if (running) {
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            int registered = 0;
            for (Path dir : watched) {
                try {
                    if (java.nio.file.Files.isDirectory(dir)) {
                        dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                        registered++;
                    }
                } catch (Exception e) {
                    Log.debug("[PackHub] 无法监控 %s: %s", dir, e.toString());
                }
            }
            if (registered == 0) {
                close();
                return;
            }
            running = true;
            thread = new Thread(this::loop, "PackHub-ModsWatcher");
            thread.setDaemon(true);
            thread.start();
            Log.info("[PackHub] 已开始监控 %d 个目录的模组变化", registered);
        } catch (Exception e) {
            Log.warn("[PackHub] 启动目录监控失败: %s", e.toString());
        }
    }

    private void loop() {
        while (running) {
            try {
                WatchKey key = watchService.take();
                boolean relevant = false;
                for (var event : key.pollEvents()) {
                    Path changed = (Path) event.context();
                    if (changed != null && changed.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                        relevant = true;
                    }
                }
                if (relevant) {
                    dirty.set(true);
                }
                if (!key.reset()) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                Log.debug("[PackHub] 目录监控异常: %s", e.toString());
            }
        }
    }

    /** 自上次调用后是否有变化。 */
    public boolean consumeDirty() {
        return dirty.getAndSet(false);
    }

    public void markDirty() {
        dirty.set(true);
    }

    @Override
    public void close() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (Exception ignored) {
                // 忽略
            }
        }
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }
}
