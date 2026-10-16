package com.packhub.http;

/** 下载进度回调。 */
public interface ProgressListener {
    ProgressListener NONE = (done, total) -> {
    };

    void onProgress(long downloaded, long total);
}
