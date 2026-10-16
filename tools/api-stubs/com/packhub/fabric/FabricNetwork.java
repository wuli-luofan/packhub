package com.packhub.fabric;
import com.packhub.platform.NetworkBridge;
/** 本地一致性检查用的替身：真实实现 fabric/src/main/java/com/packhub/fabric/FabricNetwork.java 由 CI 编译验证。 */
public final class FabricNetwork implements NetworkBridge {
    public static FabricNetwork get() { return null; }
    public static void register() { }
    public boolean isAvailable() { return false; }
    public boolean isHost() { return false; }
    public void sendToServer(byte[] payload) { }
    public void broadcastToClients(byte[] payload) { }
    public void sendToPlayer(java.util.UUID playerId, byte[] payload) { }
    public java.util.List<java.util.UUID> connectedPlayers() { return java.util.List.of(); }
    public String playerName(java.util.UUID playerId) { return "?"; }
    public void setReceiver(java.util.function.Consumer<Incoming> receiver) { }
}