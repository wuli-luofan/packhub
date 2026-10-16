package com.packhub.hotload.plugin;

/** packhub-plugin.json 的字段。 */
public final class PluginManifest {
    public String id = "";
    public String name = "";
    public String version = "1.0.0";
    public String main = "";
    public int apiVersion = 1;
    public String description = "";
    public String[] authors = new String[0];

    public boolean valid() {
        return id != null && !id.isBlank() && main != null && !main.isBlank();
    }

    public String displayName() {
        return name != null && !name.isBlank() ? name : id;
    }
}
