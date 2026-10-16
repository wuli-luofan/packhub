package com.packhub.curseforge;

/**
 * CurseForge 客户端设置。
 * <p>注意：CurseForge 的 API 需要**用户自己的 API Key**（https://console.curseforge.com/）。
 * PackHub 不内置任何 Key，也不建议把 Key 硬编码进模组分发——请在游戏内的设置界面里填。</p>
 */
public final class CurseForgeSettings {
    public String apiBase = "https://api.curseforge.com/v1";
    public String apiKey = "";
    /** downloadUrl 缺失时按 fileId 拼出的兜底直链前缀。 */
    public String fallbackDownloadBase = "https://edge.forgecdn.net/files/";
    /** 需要把 CDN 换成镜像时填这里（例如国内加速），留空表示不改写。 */
    public String cdnBaseOverride = "";
    public int connectTimeoutSeconds = 15;
    public int readTimeoutSeconds = 60;
    public int downloadRetries = 3;
    /** Minecraft 在 CurseForge 里的 gameId。 */
    public int minecraftGameId = 432;
    public String userAgent = "packhub/1.0.2 (Minecraft mod)";

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public CurseForgeSettings copy() {
        CurseForgeSettings copy = new CurseForgeSettings();
        copy.apiBase = apiBase;
        copy.apiKey = apiKey;
        copy.fallbackDownloadBase = fallbackDownloadBase;
        copy.cdnBaseOverride = cdnBaseOverride;
        copy.connectTimeoutSeconds = connectTimeoutSeconds;
        copy.readTimeoutSeconds = readTimeoutSeconds;
        copy.downloadRetries = downloadRetries;
        copy.minecraftGameId = minecraftGameId;
        copy.userAgent = userAgent;
        return copy;
    }
}
