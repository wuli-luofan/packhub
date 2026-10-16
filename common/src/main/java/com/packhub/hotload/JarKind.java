package com.packhub.hotload;

/** jar 包类型判定结果。 */
public enum JarKind {
    /** PackHub 插件格式，可真正在运行时热加载。 */
    PACKHUB_PLUGIN(true),
    /** Fabric 模组，必须重启游戏。 */
    FABRIC_MOD(false),
    /** NeoForge 模组，必须重启游戏。 */
    NEOFORGE_MOD(false),
    /** 无法识别。 */
    UNKNOWN(false);

    private final boolean hotLoadable;

    JarKind(boolean hotLoadable) {
        this.hotLoadable = hotLoadable;
    }

    public boolean hotLoadable() {
        return hotLoadable;
    }

    public String translationKey() {
        return "packhub.jar_kind." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
