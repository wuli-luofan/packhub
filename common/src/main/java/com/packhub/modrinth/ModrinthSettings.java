package com.packhub.modrinth;

/**
 * Modrinth 客户端所需的全部设置。刻意与 MC 配置类解耦，便于脱离游戏做联网测试。
 */
public final class ModrinthSettings {
    public String apiBase = "https://api.modrinth.com/v2";
    public String apiBaseFallback = "";
    public boolean useFallbackWhenPrimaryFails = true;
    public String cdnBaseOverride = "";
    public int connectTimeoutSeconds = 15;
    public int readTimeoutSeconds = 60;
    public int downloadRetries = 3;
    public String userAgent = "packhub/1.0.0 (Minecraft mod; +https://modrinth.com)";

    public ModrinthSettings() {
    }

    public ModrinthSettings apiBase(String base) {
        this.apiBase = base;
        return this;
    }

    public ModrinthSettings fallback(String base) {
        this.apiBaseFallback = base;
        return this;
    }
}
