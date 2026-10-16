package com.packhub.sync;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** 联机同步清单里的一项（就是「房主让你去下载这个文件」）。 */
public final class SyncEntry {
    public String fileName = "";
    public String url = "";
    public String sha1 = "";
    public long size;
    /** MOD / RESOURCEPACK / SHADER / DATAPACK */
    public String type = "MOD";
    public String title = "";
    public String versionNumber = "";

    public SyncEntry() {
    }

    public SyncEntry(String fileName, String url, String sha1, long size, String type,
                     String title, String versionNumber) {
        this.fileName = fileName;
        this.url = url;
        this.sha1 = sha1;
        this.size = size;
        this.type = type;
        this.title = title;
        this.versionNumber = versionNumber;
    }

    public boolean valid() {
        return fileName != null && !fileName.isBlank() && url != null && !url.isBlank();
    }

    public String describe() {
        String label = title == null || title.isBlank() ? fileName : title;
        return versionNumber == null || versionNumber.isBlank() ? label : label + " " + versionNumber;
    }

    void write(DataOutputStream out) throws IOException {
        out.writeUTF(nullSafe(fileName));
        out.writeUTF(nullSafe(url));
        out.writeUTF(nullSafe(sha1));
        out.writeLong(size);
        out.writeUTF(nullSafe(type));
        out.writeUTF(nullSafe(title));
        out.writeUTF(nullSafe(versionNumber));
    }

    static SyncEntry read(DataInputStream in) throws IOException {
        SyncEntry entry = new SyncEntry();
        entry.fileName = in.readUTF();
        entry.url = in.readUTF();
        entry.sha1 = in.readUTF();
        entry.size = in.readLong();
        entry.type = in.readUTF();
        entry.title = in.readUTF();
        entry.versionNumber = in.readUTF();
        return entry;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return describe() + " (" + fileName + ")";
    }
}
