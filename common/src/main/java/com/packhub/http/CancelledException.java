package com.packhub.http;

import java.io.IOException;

/** 被用户取消（不是网络错误，因此不重试）。 */
public class CancelledException extends IOException {
    public CancelledException(String message) {
        super(message);
    }
}
