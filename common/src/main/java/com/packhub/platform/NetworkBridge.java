package com.packhub.platform;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 加载器无关的网络通道：只在 PackHub 自己的客户端/服务端之间传字节。
 * <p>具体实现分别由 Fabric 的 Payload 网络与 NeoForge 的 Payload 网络提供；
 * 拿不到（例如对面没装 PackHub、或当前不是联机）时用 {@link #NOOP}，
 * 上层只会在界面上显示「不可用」，不会崩。</p>
 */
public interface NetworkBridge {

    /** 一条收到的消息。 */
    record Incoming(byte[] data, UUID sender, String senderName, boolean fromServer) {
    }

    /** 什么都不做的实现。 */
    NetworkBridge NOOP = new NetworkBridge() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public boolean isHost() {
            return false;
        }

        @Override
        public void sendToServer(byte[] payload) {
        }

        @Override
        public void broadcastToClients(byte[] payload) {
        }

        @Override
        public void sendToPlayer(UUID playerId, byte[] payload) {
        }

        @Override
        public List<UUID> connectedPlayers() {
            return List.of();
        }

        @Override
        public String playerName(UUID playerId) {
            return "?";
        }

        @Override
        public void setReceiver(Consumer<Incoming> receiver) {
        }
    };

    /** 通道是否可用（双方都装了 PackHub 且处于联机状态）。 */
    boolean isAvailable();

    /** 当前进程是否在承载服务器（单人 / 局域网房主 / 专用服务器）。 */
    boolean isHost();

    /** 客户端 -> 服务端。 */
    void sendToServer(byte[] payload);

    /** 服务端 -> 所有装了我们通道的客户端。 */
    void broadcastToClients(byte[] payload);

    /** 服务端 -> 指定玩家。 */
    void sendToPlayer(UUID playerId, byte[] payload);

    /** 服务端视角：当前在线的玩家。 */
    List<UUID> connectedPlayers();

    String playerName(UUID playerId);

    /** 注册接收回调（收到消息时在游戏线程外被调用，实现方负责切线程）。 */
    void setReceiver(Consumer<Incoming> receiver);
}
