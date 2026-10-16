package com.packhub.source;

/** CurseForge 调用失败（最常见的是缺少 / 无效 API Key）。 */
public class CurseForgeException extends SourceException {
    public CurseForgeException(String message, int statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }

    public CurseForgeException(String message, int statusCode) {
        super(message, statusCode);
    }

    public static CurseForgeException missingKey() {
        return new CurseForgeException(
                "未配置 CurseForge API Key：请到 https://console.curseforge.com/ 申请，然后在 PackHub 设置里填入", 401);
    }
}
