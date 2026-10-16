package com.packhub.neoforge;
import com.packhub.platform.NetworkBridge;
import net.neoforged.bus.api.IEventBus;
/** 本地一致性检查用的替身：真实实现由 CI 编译验证。 */
public final class NeoForgeNetwork implements NetworkBridge {
    public static NeoForgeNetwork get() { return null; }
    public static void register(IEventBus modEventBus) { }
    public boolean isAvailable() { return false; }
    public boolean isHost() { return false; }
    public void sendToServer(byte[] payload) { }
    public void broadcastToClients(byte[] payload) { }
    public void sendToPlayer(java.util.UUID playerId, byte[] payload) { }
    public java.util.List<java.util.UUID> connectedPlayers() { return java.util.List.of(); }
    public String playerName(java.util.UUID playerId) { return "?"; }
    public void setReceiver(java.util.function.Consumer<Incoming> receiver) { }
}