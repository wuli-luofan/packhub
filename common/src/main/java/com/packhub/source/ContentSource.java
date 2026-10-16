package com.packhub.source;

import java.util.Locale;

/** PackHub 支持的内容来源。 */
public enum ContentSource {
    MODRINTH("modrinth", "Modrinth"),
    CURSEFORGE("curseforge", "CurseForge");

    private final String id;
    private final String displayName;

    ContentSource(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static ContentSource byId(String value) {
        if (value != null) {
            for (ContentSource source : values()) {
                if (source.id.equalsIgnoreCase(value.trim())) {
                    return source;
                }
            }
        }
        return MODRINTH;
    }

    public ContentSource next() {
        ContentSource[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String translationKey() {
        return "packhub.source." + id;
    }

    @Override
    public String toString() {
        return displayName + " (" + id.toLowerCase(Locale.ROOT) + ")";
    }
}
