package com.packhub.hotload;

/** 一次安装后热加载的结果。 */
public final class HotLoadOutcome {
    private final boolean hotReloaded;
    private final boolean needsRestart;
    private final String message;

    public HotLoadOutcome(boolean hotReloaded, boolean needsRestart, String message) {
        this.hotReloaded = hotReloaded;
        this.needsRestart = needsRestart;
        this.message = message;
    }

    public static HotLoadOutcome reloaded(String message) {
        return new HotLoadOutcome(true, false, message);
    }

    public static HotLoadOutcome restartRequired(String message) {
        return new HotLoadOutcome(false, true, message);
    }

    public static HotLoadOutcome skipped(String message) {
        return new HotLoadOutcome(false, false, message);
    }

    public boolean hotReloaded() {
        return hotReloaded;
    }

    public boolean needsRestart() {
        return needsRestart;
    }

    public String message() {
        return message;
    }
}
