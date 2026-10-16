package com.packhub.modrinth.model;

/** 版本里的一个文件。 */
public final class VersionFile {
    public String url = "";
    public String filename = "";
    public boolean primary;
    public long size;
    public String sha1 = "";
    public String sha512 = "";

    public String prettySize() {
        double kb = size / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KiB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MiB", mb);
        }
        return String.format("%.2f GiB", mb / 1024.0);
    }
}
