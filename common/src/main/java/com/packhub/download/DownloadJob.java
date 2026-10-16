package com.packhub.download;

import com.packhub.http.CancelToken;
import com.packhub.modrinth.ContentType;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/** 一个正在进行的下载任务，供界面显示进度与取消。 */
public final class DownloadJob {
    public enum Status { QUEUED, DOWNLOADING, VERIFYING, DONE, FAILED, CANCELLED }

    private static final AtomicLong COUNTER = new AtomicLong();

    public final long id = COUNTER.incrementAndGet();
    public final String projectTitle;
    public final String fileName;
    public final ContentType type;
    public final long expectedSize;

    private final CancelToken cancelToken = new CancelToken();
    private volatile Status status = Status.QUEUED;
    private volatile long downloaded;
    private volatile long total;
    private volatile String message = "";
    private volatile Path result;

    public DownloadJob(String projectTitle, String fileName, ContentType type, long expectedSize) {
        this.projectTitle = projectTitle;
        this.fileName = fileName;
        this.type = type;
        this.expectedSize = expectedSize;
        this.total = expectedSize;
    }

    public Status status() {
        return status;
    }

    public void status(Status value) {
        this.status = value;
    }

    public long downloaded() {
        return downloaded;
    }

    public long total() {
        return total;
    }

    public String message() {
        return message;
    }

    public void message(String value) {
        this.message = value == null ? "" : value;
    }

    public Path result() {
        return result;
    }

    public void result(Path value) {
        this.result = value;
    }

    public void progress(long done, long all) {
        this.downloaded = done;
        if (all > 0) {
            this.total = all;
        }
    }

    public float fraction() {
        if (total <= 0) {
            return status == Status.DONE ? 1f : 0f;
        }
        return Math.max(0f, Math.min(1f, downloaded / (float) total));
    }

    public boolean finished() {
        return status == Status.DONE || status == Status.FAILED || status == Status.CANCELLED;
    }

    // ------------------------------------------------------------ 取消

    /** 下载线程会周期性检查这个令牌。 */
    public CancelToken cancelToken() {
        return cancelToken;
    }

    public boolean cancelled() {
        return cancelToken.isCancelled();
    }

    /** 请求取消：排队中的任务会直接标记为已取消，下载中的会在下一块数据处停下。 */
    public void requestCancel(String reason) {
        cancelToken.cancel(reason);
        if (status == Status.QUEUED) {
            status = Status.CANCELLED;
            message = cancelToken.reason();
        }
    }

    public String describe() {
        return switch (status) {
            case QUEUED -> "排队中";
            case DOWNLOADING -> String.format("下载中 %.0f%%", fraction() * 100f);
            case VERIFYING -> "校验中";
            case DONE -> "完成";
            case FAILED -> "失败: " + message;
            case CANCELLED -> "已取消";
        };
    }
}
