package com.packhub.source;

/** 内容源（Modrinth / CurseForge）调用失败的公共父类。 */
public class SourceException extends Exception {
    private final int statusCode;

    public SourceException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public SourceException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean isRateLimited() {
        return statusCode == 429;
    }

    public boolean isAuthProblem() {
        return statusCode == 401 || statusCode == 403;
    }
}
