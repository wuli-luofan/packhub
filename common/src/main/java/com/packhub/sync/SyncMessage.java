package com.packhub.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 一条同步消息（房主 ↔ 玩家）。
 * <p>用一个字节的类型 + 简单字段，双方都跑 PackHub 时才能互通；对方没装 PackHub 则频道不存在，
 * 房主端只是看不到这名玩家。</p>
 */
public final class SyncMessage {

    public enum Kind {
        /** 客户端连上后向房主自报家门。 */
        HELLO,
        /** 房主 -> 客户端：这是需要你下载的清单。 */
        MANIFEST,
        /** 客户端 -> 房主：我接受，开始下载。 */
        ACCEPT,
        /** 客户端 -> 房主：我拒绝（配置不允许 / 玩家手动取消）。 */
        DECLINE,
        /** 客户端 -> 房主：进度。 */
        PROGRESS,
        /** 客户端 -> 房主：我全部装好了。 */
        READY,
        /** 客户端 -> 房主：我失败了。 */
        FAILED,
        /** 房主 -> 客户端：现在一起重启。 */
        RESTART,
        /** 房主 -> 客户端：取消这次同步。 */
        CANCEL
    }

    public Kind kind = Kind.HELLO;
    public String text = "";
    public String playerName = "";
    public int index;
    public int total;
    public SyncManifest manifest;

    public SyncMessage() {
    }

    public SyncMessage(Kind kind) {
        this.kind = kind;
    }

    public static SyncMessage hello(String playerName) {
        SyncMessage message = new SyncMessage(Kind.HELLO);
        message.playerName = playerName == null ? "" : playerName;
        return message;
    }

    public static SyncMessage manifest(SyncManifest manifest) {
        SyncMessage message = new SyncMessage(Kind.MANIFEST);
        message.manifest = manifest;
        return message;
    }

    public static SyncMessage progress(String fileName, int index, int total) {
        SyncMessage message = new SyncMessage(Kind.PROGRESS);
        message.text = fileName == null ? "" : fileName;
        message.index = index;
        message.total = total;
        return message;
    }

    public static SyncMessage withText(Kind kind, String text) {
        SyncMessage message = new SyncMessage(kind);
        message.text = text == null ? "" : text;
        return message;
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(1);
                out.writeByte(kind.ordinal());
                out.writeUTF(text == null ? "" : text);
                out.writeUTF(playerName == null ? "" : playerName);
                out.writeInt(index);
                out.writeInt(total);
                boolean hasManifest = manifest != null;
                out.writeBoolean(hasManifest);
                if (hasManifest) {
                    byte[] payload = manifest.encode();
                    out.writeInt(payload.length);
                    out.write(payload);
                }
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("编码同步消息失败", e);
        }
    }

    public static SyncMessage decode(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = in.readInt();
            if (version != 1) {
                throw new IOException("不支持的同步协议版本: " + version);
            }
            SyncMessage message = new SyncMessage();
            int ordinal = in.readByte();
            Kind[] kinds = Kind.values();
            if (ordinal < 0 || ordinal >= kinds.length) {
                throw new IOException("未知的同步消息类型: " + ordinal);
            }
            message.kind = kinds[ordinal];
            message.text = in.readUTF();
            message.playerName = in.readUTF();
            message.index = in.readInt();
            message.total = in.readInt();
            if (in.readBoolean()) {
                int length = in.readInt();
                if (length < 0 || length > 8 * 1024 * 1024) {
                    throw new IOException("清单长度异常: " + length);
                }
                byte[] payload = new byte[length];
                in.readFully(payload);
                message.manifest = SyncManifest.decode(payload);
            }
            return message;
        }
    }
}
