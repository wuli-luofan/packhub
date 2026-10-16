package com.packhub.download;

import com.packhub.PackHub;
import com.packhub.config.PackHubConfig;
import com.packhub.curseforge.CurseForgeClient;
import com.packhub.hotload.HotLoadOutcome;
import com.packhub.http.ProgressListener;
import com.packhub.installed.InstallRegistry;
import com.packhub.installed.InstalledModIndex;
import com.packhub.modrinth.ContentType;
import com.packhub.modrinth.ModrinthClient;
import com.packhub.modrinth.model.ProjectSummary;
import com.packhub.modrinth.model.ProjectVersion;
import com.packhub.modrinth.model.VersionDependency;
import com.packhub.modrinth.model.VersionFile;
import com.packhub.platform.Platform;
import com.packhub.source.ContentSource;
import com.packhub.source.SourceException;
import com.packhub.util.Hashes;
import com.packhub.util.Log;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 下载与安装调度：把 Modrinth / CurseForge 的文件落到正确的目录，
 * 可选地自动解析必选前置，并在结束后触发热加载流程。
 */
public class DownloadManager {

    public interface Listener {
        void onInstalled(InstallResult result);

        void onFailed(String title, String error);

        default void onCancelled(String title) {
        }
    }

    private final Platform platform;
    private final PackHubConfig config;
    private final ModrinthClient modrinth;
    private final CurseForgeClient curseForge;
    private final InstallRegistry registry;
    private final ExecutorService downloads;
    private final ExecutorService metadata;
    private final Map<Long, DownloadJob> jobs = new ConcurrentHashMap<>();
    private final Set<String> installing = ConcurrentHashMap.newKeySet();
    private volatile Listener listener;

    public DownloadManager(Platform platform, PackHubConfig config,
                           ModrinthClient modrinth, CurseForgeClient curseForge) {
        this.platform = platform;
        this.config = config;
        this.modrinth = modrinth;
        this.curseForge = curseForge;
        this.registry = new InstallRegistry(config.file() != null
                ? config.file().getParent().resolve("install-registry.json")
                : platform.configDir().resolve("packhub").resolve("install-registry.json"));
        int poolSize = Math.max(1, config.maxConcurrentDownloads);
        this.downloads = Executors.newFixedThreadPool(poolSize, namedFactory("PackHub-Download"));
        this.metadata = Executors.newFixedThreadPool(2, namedFactory("PackHub-Metadata"));
    }

    /** 安装记录（同时是联机同步的清单来源）。 */
    public InstallRegistry registry() {
        return registry;
    }

    public void listener(Listener value) {
        this.listener = value;
    }

    public List<DownloadJob> jobs() {
        List<DownloadJob> list = new ArrayList<>(jobs.values());
        list.sort((a, b) -> Long.compare(b.id, a.id));
        return Collections.unmodifiableList(list);
    }

    public List<DownloadJob> activeJobs() {
        List<DownloadJob> list = new ArrayList<>();
        for (DownloadJob job : jobs.values()) {
            if (!job.finished()) {
                list.add(job);
            }
        }
        return list;
    }

    public boolean busy() {
        return !activeJobs().isEmpty();
    }

    // ------------------------------------------------------------ 取消 / 总体进度

    /** 取消单个任务。 */
    public boolean cancelJob(long jobId) {
        DownloadJob job = jobs.get(jobId);
        if (job == null || job.finished()) {
            return false;
        }
        job.requestCancel("用户取消");
        Log.info("[PackHub] 已请求取消: %s", job.fileName);
        return true;
    }

    /** 取消当前所有未完成任务。 */
    public int cancelAll() {
        int count = 0;
        for (DownloadJob job : jobs.values()) {
            if (!job.finished()) {
                job.requestCancel("用户取消全部");
                count++;
            }
        }
        Log.info("[PackHub] 已请求取消全部任务（%d 个）", count);
        return count;
    }

    /** 总体进度：返回 {已下载字节, 总字节}，供进度界面画总进度条。 */
    public long[] overallProgress() {
        long done = 0;
        long total = 0;
        for (DownloadJob job : jobs.values()) {
            total += Math.max(0, job.total());
            done += Math.max(0, Math.min(job.downloaded(), Math.max(job.total(), job.downloaded())));
        }
        return new long[]{done, total};
    }

    /** 是否所有任务都已结束。 */
    public boolean allFinished() {
        return activeJobs().isEmpty();
    }

    // ------------------------------------------------------------ 查询

    /** 异步拉取某项目的可用版本（自动按来源分派）。 */
    public CompletableFuture<List<ProjectVersion>> fetchVersions(ProjectSummary project, ContentType type) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<ProjectVersion> versions = listVersions(project.source, project.idOrSlug(), type,
                        PackHub.MINECRAFT_VERSION, loaderFilter(type));
                if (versions.isEmpty()) {
                    // 退一步：不带游戏版本过滤，至少让用户看到有什么
                    versions = listVersions(project.source, project.idOrSlug(), type, null, loaderFilter(type));
                }
                return versions;
            } catch (SourceException e) {
                Log.warn("[PackHub] 获取 %s 的版本失败: %s", project.title, e.getMessage());
                return List.of();
            }
        }, metadata);
    }

    /** 按来源列版本。 */
    public List<ProjectVersion> listVersions(ContentSource source, String projectId, ContentType type,
                                             String gameVersion, String loader) throws SourceException {
        if (source == ContentSource.CURSEFORGE) {
            return curseForge.listFiles(projectId, type, gameVersion, loader);
        }
        try {
            return modrinth.listVersions(projectId, type, gameVersion, loader);
        } catch (SourceException e) {
            throw e;
        }
    }

    private String loaderFilter(ContentType type) {
        if (!type.loaderSpecific()) {
            return null;
        }
        return currentLoaderId();
    }

    /** 当前加载器在内容源里的标识：fabric / neoforge。 */
    public String currentLoaderId() {
        String loader = platform.loaderName().toLowerCase(Locale.ROOT);
        return loader.contains("neo") ? "neoforge" : "fabric";
    }

    // ------------------------------------------------------------ 安装

    /** 安装指定版本的主文件（含前置解析）。 */
    public CompletableFuture<InstallResult> install(ProjectSummary project, ProjectVersion version,
                                                    VersionFile file, ContentType type,
                                                    boolean enableAfterInstall) {
        String key = (project == null ? "?" : project.idOrSlug()) + "@" + (file == null ? "?" : file.filename);
        if (!installing.add(key)) {
            return CompletableFuture.failedFuture(new IllegalStateException("该项目正在安装中"));
        }
        DownloadJob job = new DownloadJob(
                project != null ? project.title : "未知项目",
                file != null ? file.filename : "unknown",
                type,
                file != null ? file.size : 0);
        jobs.put(job.id, job);
        job.status(DownloadJob.Status.QUEUED);

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (config.autoResolveDependencies) {
                    resolveDependencies(version, type, 0, job.cancelToken());
                }
                job.cancelToken().throwIfCancelled();
                job.status(DownloadJob.Status.DOWNLOADING);
                Path target = InstallTargets.resolve(platform, type).resolve(file.filename);
                if (isSameFile(target, file)) {
                    job.status(DownloadJob.Status.DONE);
                    job.result(target);
                    job.message("已存在相同文件，跳过下载");
                    InstallResult result = new InstallResult(project, version, file, type, target, false, false,
                            "已安装（文件已存在）");
                    registry.record(result);
                    notifyInstalled(result);
                    return result;
                }
                downloadFile(version != null ? version.source : ContentSource.MODRINTH, file, target,
                        job::progress, job.cancelToken());
                job.status(DownloadJob.Status.VERIFYING);

                HotLoadOutcome outcome = PackHub.hotLoad().apply(type, target, enableAfterInstall);
                job.status(DownloadJob.Status.DONE);
                job.result(target);
                job.message(outcome.message());

                String message = outcome.needsRestart()
                        ? "已下载，需重启或软重启后生效"
                        : (outcome.hotReloaded() ? "已下载并热加载" : "已下载");
                InstallResult result = new InstallResult(project, version, file, type, target,
                        outcome.hotReloaded(), outcome.needsRestart(), message);
                registry.record(result);
                notifyInstalled(result);
                return result;
            } catch (SourceException e) {
                if (job.cancelled()) {
                    job.status(DownloadJob.Status.CANCELLED);
                    job.message("已取消");
                    notifyCancelled(job.projectTitle);
                    throw new RuntimeException(e);
                }
                job.status(DownloadJob.Status.FAILED);
                job.message(e.getMessage());
                notifyFailed(job.projectTitle, e.getMessage());
                throw new RuntimeException(e);
            } catch (com.packhub.http.CancelledException e) {
                job.status(DownloadJob.Status.CANCELLED);
                job.message("已取消");
                notifyCancelled(job.projectTitle);
                throw new RuntimeException(e);
            } catch (Throwable t) {
                if (job.cancelled()) {
                    job.status(DownloadJob.Status.CANCELLED);
                    job.message("已取消");
                    notifyCancelled(job.projectTitle);
                    throw new RuntimeException(t);
                }
                job.status(DownloadJob.Status.FAILED);
                job.message(String.valueOf(t));
                notifyFailed(job.projectTitle, String.valueOf(t));
                throw new RuntimeException(t);
            } finally {
                installing.remove(key);
            }
        }, downloads);
    }

    /** 按来源下载单个文件。 */
    public Path downloadFile(ContentSource source, VersionFile file, Path target, ProgressListener listener)
            throws SourceException {
        return downloadFile(source, file, target, listener, com.packhub.http.CancelToken.NONE);
    }

    /** 按来源下载单个文件，支持取消。 */
    public Path downloadFile(ContentSource source, VersionFile file, Path target, ProgressListener listener,
                             com.packhub.http.CancelToken token) throws SourceException {
        if (source == ContentSource.CURSEFORGE) {
            return curseForge.download(file, target, listener, token);
        }
        return modrinth.download(file, target, listener, token);
    }

    /** 只下载文件、不触发热加载（供插件安装等场景复用）。 */
    public Path downloadOnly(ContentSource source, VersionFile file, Path target) throws SourceException {
        return downloadFile(source, file, target, ProgressListener.NONE);
    }

    private boolean isSameFile(Path target, VersionFile file) {
        try {
            if (!Files.isRegularFile(target)) {
                return false;
            }
            if (file.sha1 != null && !file.sha1.isBlank()) {
                return file.sha1.equalsIgnoreCase(Hashes.sha1(target));
            }
            return Files.size(target) == file.size && file.size > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 递归安装必选前置（两个来源各自解析）；同一个取消令牌贯穿到底。 */
    private void resolveDependencies(ProjectVersion version, ContentType type, int depth,
                                     com.packhub.http.CancelToken token) {
        if (version == null || depth >= config.maxDependencyDepth) {
            return;
        }
        ContentSource source = version.source;
        List<String> installedIds = new ArrayList<>();
        for (String id : InstalledModIndex.installedModIds(platform)) {
            installedIds.add(normalize(id));
        }
        for (VersionDependency dependency : version.dependencies) {
            if (!dependency.isRequired() || dependency.projectId == null || dependency.projectId.isBlank()) {
                continue;
            }
            try {
                ProjectSummary project = source == ContentSource.CURSEFORGE
                        ? curseForge.project(dependency.projectId)
                        : modrinth.project(dependency.projectId);
                if (installedIds.contains(normalize(project.slug))
                        || installedIds.contains(normalize(project.projectId))) {
                    Log.info("[PackHub] 前置 %s 已安装，跳过", project.title);
                    continue;
                }
                List<ProjectVersion> versions = listVersions(source, project.idOrSlug(), type,
                        PackHub.MINECRAFT_VERSION, loaderFilter(type));
                ProjectVersion candidate = versions.isEmpty() ? null : versions.get(0);
                if (candidate == null) {
                    Log.warn("[PackHub] 前置 %s 没有匹配 %s 的版本", project.title, PackHub.MINECRAFT_VERSION);
                    continue;
                }
                VersionFile file = candidate.primaryFile();
                if (file == null) {
                    continue;
                }
                Path target = InstallTargets.resolve(platform, type).resolve(file.filename);
                if (isSameFile(target, file)) {
                    continue;
                }
                Log.info("[PackHub] 自动安装前置 %s (%s)", project.title, file.filename);
                DownloadJob job = new DownloadJob(project.title + "（前置）", file.filename, type, file.size);
                jobs.put(job.id, job);
                job.status(DownloadJob.Status.DOWNLOADING);
                downloadFile(source, file, target, job::progress, token);
                job.status(DownloadJob.Status.DONE);
                job.result(target);
                registry.record(new InstallResult(project, candidate, file, type, target, false, false, "前置已安装"));
                resolveDependencies(candidate, type, depth + 1, token);
            } catch (Throwable t) {
                Log.warn("[PackHub] 处理前置 %s 失败: %s", dependency.projectId, t.toString());
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private void notifyInstalled(InstallResult result) {
        Listener current = listener;
        if (current != null) {
            withClient(() -> current.onInstalled(result));
        }
    }

    private void notifyFailed(String title, String error) {
        Listener current = listener;
        if (current != null) {
            withClient(() -> current.onFailed(title, error));
        }
    }

    private void notifyCancelled(String title) {
        Listener current = listener;
        if (current != null) {
            withClient(() -> current.onCancelled(title));
        }
    }

    private static void withClient(Runnable action) {
        try {
            Minecraft.getInstance().execute(action);
        } catch (Throwable t) {
            action.run();
        }
    }

    /** 客户端 tick：清理已经播报过的旧任务，避免界面无限增长。 */
    public void tick() {
        if (jobs.size() < 32) {
            return;
        }
        Map<Long, DownloadJob> keep = new LinkedHashMap<>();
        List<DownloadJob> ordered = jobs();
        int kept = 0;
        for (DownloadJob job : ordered) {
            if (job.finished()) {
                if (kept++ >= 8) {
                    continue;
                }
            }
            keep.put(job.id, job);
        }
        jobs.keySet().retainAll(keep.keySet());
    }

    public void shutdown() {
        downloads.shutdownNow();
        metadata.shutdownNow();
        try {
            downloads.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
