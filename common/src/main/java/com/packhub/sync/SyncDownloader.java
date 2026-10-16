package com.packhub.sync;

import com.packhub.http.CancelToken;
import com.packhub.http.ProgressListener;
import com.packhub.source.SourceException;
import com.packhub.util.Hashes;
import com.packhub.util.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 按清单把文件下载到本地（客户端侧执行）。
 * <p>刻意不依赖 Minecraft：真正的下载动作由调用方以 {@link Fetcher} 注入，
 * 因此这一整套流程可以在离线单测里跑通。</p>
 */
public final class SyncDownloader {

    /** 实际下载一个文件的方式（由 DownloadManager 提供）。 */
    public interface Fetcher {
        Path fetch(SyncEntry entry, Path target, ProgressListener listener, CancelToken token)
                throws java.io.IOException, SourceException;
    }

    /** 一次同步的执行结果。 */
    public static final class Outcome {
        public final List<SyncEntry> downloaded = new ArrayList<>();
        public final List<SyncEntry> skipped = new ArrayList<>();
        public final List<String> failures = new ArrayList<>();
        public boolean cancelled;
        public int total;

        public int completed() {
            return downloaded.size() + skipped.size() + failures.size();
        }

        public boolean allOk() {
            return failures.isEmpty() && !cancelled;
        }

        public String summary() {
            return "已安装 " + downloaded.size() + " · 已存在 " + skipped.size()
                    + " · 失败 " + failures.size() + (cancelled ? " · 已取消" : "");
        }
    }

    private final Path targetDir;
    private final Fetcher fetcher;

    public SyncDownloader(Path targetDir, Fetcher fetcher) {
        this.targetDir = targetDir;
        this.fetcher = fetcher;
    }

    /**
     * 顺序下载清单里的全部文件。
     *
     * @param onStatus 每换一个文件回调一次（给界面显示当前在装什么）
     * @param onIndex  每完成一个文件回调一次（index, total），给房主端发进度
     */
    public Outcome run(SyncManifest manifest, CancelToken token, Consumer<String> onStatus,
                       BiConsumer<Integer, Integer> onIndex) {
        Outcome outcome = new Outcome();
        if (manifest == null) {
            return outcome;
        }
        outcome.total = manifest.entries.size();
        if (onStatus != null) {
            onStatus.accept("准备下载 " + outcome.total + " 个文件…");
        }

        int index = 0;
        for (SyncEntry entry : manifest.entries) {
            if (token.isCancelled()) {
                outcome.cancelled = true;
                break;
            }
            if (!entry.valid()) {
                outcome.failures.add(entry.fileName + ": 清单项无效");
                index++;
                continue;
            }
            try {
                Path target = targetDir.resolve(entry.fileName);
                if (isAlreadyThere(target, entry)) {
                    outcome.skipped.add(entry);
                    Log.info("[PackHub] 同步跳过（已存在且校验一致）: %s", entry.fileName);
                } else {
                    if (onStatus != null) {
                        onStatus.accept("正在下载 " + entry.describe() + "（" + (index + 1) + "/" + outcome.total + "）");
                    }
                    fetcher.fetch(entry, target, ProgressListener.NONE, token);
                    outcome.downloaded.add(entry);
                    Log.info("[PackHub] 同步完成: %s", entry.fileName);
                }
            } catch (SourceException e) {
                if (token.isCancelled()) {
                    outcome.cancelled = true;
                    break;
                }
                outcome.failures.add(entry.fileName + ": " + e.getMessage());
                Log.warn("[PackHub] 同步失败 %s: %s", entry.fileName, e.getMessage());
            } catch (Throwable t) {
                outcome.failures.add(entry.fileName + ": " + t);
                Log.warn("[PackHub] 同步异常 %s: %s", entry.fileName, t.toString());
            }
            index++;
            if (onIndex != null) {
                onIndex.accept(index, outcome.total);
            }
        }
        if (onStatus != null) {
            onStatus.accept(outcome.summary());
        }
        return outcome;
    }

    /** 文件已存在且 sha1 一致就不用重复下载。 */
    public static boolean isAlreadyThere(Path target, SyncEntry entry) {
        try {
            if (!Files.isRegularFile(target)) {
                return false;
            }
            if (entry.sha1 == null || entry.sha1.isBlank()) {
                return entry.size > 0 && Files.size(target) == entry.size;
            }
            return entry.sha1.equalsIgnoreCase(Hashes.sha1(target));
        } catch (Exception e) {
            return false;
        }
    }
}
