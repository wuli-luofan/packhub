package com.packhub.modrinth;

/**
 * PackHub 支持的内容类型。Modrinth 的 project_type 与安装目录的映射都在这里。
 */
public enum ContentType {
    MOD("mod", "mods", true),
    RESOURCEPACK("resourcepack", "resourcepacks", false),
    SHADER("shader", "shaderpacks", false),
    DATAPACK("datapack", "datapacks", false),
    MODPACK("modpack", "modpacks", false);

    private final String modrinthType;
    private final String installFolder;
    private final boolean loaderSpecific;

    ContentType(String modrinthType, String installFolder, boolean loaderSpecific) {
        this.modrinthType = modrinthType;
        this.installFolder = installFolder;
        this.loaderSpecific = loaderSpecific;
    }

    public String modrinthType() {
        return modrinthType;
    }

    public String installFolder() {
        return installFolder;
    }

    /** 模组需要按加载器过滤，资源包/光影不需要。 */
    public boolean loaderSpecific() {
        return loaderSpecific;
    }

    /** 是否可以在游戏运行时真正热加载（不重启）。 */
    public boolean hotReloadable() {
        return this == RESOURCEPACK || this == SHADER || this == DATAPACK;
    }

    public static ContentType byModrinthType(String type) {
        for (ContentType value : values()) {
            if (value.modrinthType.equalsIgnoreCase(type)) {
                return value;
            }
        }
        return MOD;
    }

    public String translationKey() {
        return "packhub.type." + modrinthType;
    }
}
