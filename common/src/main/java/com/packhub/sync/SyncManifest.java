package com.packhub.sync;

import com.packhub.installed.InstallRegistry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 房主下发的同步清单：一批「请你去下载这些文件」。
 * <p>用简单的长度前缀二进制格式编码，不依赖 Gson 反射，版本间也稳定。</p>
 */
public final class SyncManifest {
    private static final int MAX_ENTRIES = 512;

    public String hostName = "";
    public long createdAt;
    public final List<SyncEntry> entries = new ArrayList<>();

    public SyncManifest() {
    }

    public SyncManifest(String hostName, List<SyncEntry> entries) {
        this.hostName = hostName == null ? "" : hostName;
        this.createdAt = System.currentTimeMillis();
        if (entries != null) {
            this.entries.addAll(entries);
        }
    }

    /** 从安装记录里挑出「本机确实还有、并且能分享」的模组条目。 */
    public static SyncManifest fromRegistry(String hostName, InstallRegistry registry,
                                            java.nio.file.Path modsDir, boolean includeResourcePacks,
                                            int maxFileSizeMb) {
        List<SyncEntry> entries = new ArrayList<>();
        long maxBytes = Math.max(1, maxFileSizeMb) * 1024L * 1024L;
        for (InstallRegistry.Entry record : registry.entries()) {
            boolean isMod = "MOD".equals(record.type);
            if (!isMod && !includeResourcePacks) {
                continue;
            }
            if (!record.shareable() || record.size > maxBytes) {
                continue;
            }
            if (isMod && modsDir != null && !java.nio.file.Files.isRegularFile(modsDir.resolve(record.fileName))) {
                continue;
            }
            entries.add(new SyncEntry(record.fileName, record.url, record.sha1, record.size,
                    record.type, record.title, record.versionNumber));
        }
        return new SyncManifest(hostName, entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(1);                 // 协议版本
                out.writeUTF(hostName == null ? "" : hostName);
                out.writeLong(createdAt);
                int count = Math.min(entries.size(), MAX_ENTRIES);
                out.writeInt(count);
                for (int i = 0; i < count; i++) {
                    entries.get(i).write(out);
                }
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("编码同步清单失败", e);
        }
    }

    public static SyncManifest decode(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = in.readInt();
            if (version != 1) {
                throw new IOException("不支持的同步协议版本: " + version);
            }
            SyncManifest manifest = new SyncManifest();
            manifest.hostName = in.readUTF();
            manifest.createdAt = in.readLong();
            int count = in.readInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw new IOException("清单条目数异常: " + count);
            }
            for (int i = 0; i < count; i++) {
                manifest.entries.add(SyncEntry.read(in));
            }
            return manifest;
        }
    }
}
