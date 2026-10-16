package com.packhub.http;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 下载/安装的取消令牌。
 * <p>下载线程在每写一块数据前检查一次，被取消时立刻停止并把已写入的 .part 文件删掉，
 * 不会留下半截文件。</p>
 */
public final class CancelToken {

    /** 一个永远不会被取消的令牌，给不需要取消能力的调用方用。 */
    public static final CancelToken NONE = new CancelToken();

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile String reason = "已取消";

    public boolean isCancelled() {
        return cancelled.get();
    }

    public String reason() {
        return reason;
    }

    public void cancel() {
        cancel("已取消");
    }

    public void cancel(String reason) {
        this.reason = reason == null || reason.isBlank() ? "已取消" : reason;
        cancelled.set(true);
    }

    public void throwIfCancelled() throws CancelledException {
        if (cancelled.get()) {
            throw new CancelledException(reason);
        }
    }
}
