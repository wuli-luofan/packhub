package com.packhub.fabric;

import com.packhub.platform.NetworkBridge;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fabric 侧的联机通道：用 Fabric API 的 Payload 网络在 PackHub 客户端与房主之间传字节。
 * <p>数据统一包成 Base64 字符串（{@code ByteBufCodecs.STRING_UTF8}），
 * 这样不用为每种结构写 codec，协议版本也能自己控制。</p>
 */
public final class FabricNetwork implements NetworkBridge {

    private static final String NAMESPACE = "packhub";
    private static final String PATH = "sync";
    private static FabricNetwork instance;

    private volatile Consumer<Incoming> receiver = incoming -> {
    };

    /** 通道 ID：26.x 叫 Identifier，构造方法名在版本间变过，这里反射兜底。 */
    public static final Identifier CHANNEL = createChannel();

    /** 一个只带字符串的数据包。 */
    public record Payload(String data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Payload> TYPE = new CustomPacketPayload.Type<>(CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Payload::data, Payload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static FabricNetwork get() {
        if (instance == null) {
            instance = new FabricNetwork();
        }
        return instance;
    }

    /** 在客户端初始化时注册数据包类型与收发器（整合服务器同样走这里）。 */
    public static void register() {
        registerPayloadTypes();

        ServerPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, context) -> {
            ServerPlayer sender = context.player();
            MinecraftServer server = server();
            Runnable work = () -> dispatchFromClient(payload, sender);
            // 服务端线程外收到时要切回服务端线程（整合服务器与客户端线程不同）
            if (server != null) {
                server.execute(work);
            } else {
                work.run();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, context) ->
                Minecraft.getInstance().execute(() -> dispatchFromServer(payload)));
    }

    /**
     * 注册 play 阶段的数据包类型。
     * <p>方法名由 CI 的 API 探测确认：26.3 的 Fabric API 用
     * {@code serverboundPlay()}（C2S）与 {@code clientboundPlay()}（S2C），
     * 旧名字 {@code playC2S()/playS2C()} 已不存在。</p>
     */
    private static void registerPayloadTypes() {
        PayloadTypeRegistry.serverboundPlay().register(Payload.TYPE, Payload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
    }

    private static void dispatchFromClient(Payload payload, ServerPlayer sender) {
        if (instance == null) {
            return;
        }
        UUID id = sender.getUUID();
        String name = sender.getName().getString();
        instance.receiver.accept(new Incoming(decode(payload), id, name, false));
    }

    private static void dispatchFromServer(Payload payload) {
        if (instance == null) {
            return;
        }
        instance.receiver.accept(new Incoming(decode(payload), null, "", true));
    }

    // ------------------------------------------------------------ NetworkBridge

    @Override
    public boolean isAvailable() {
        try {
            return ClientPlayNetworking.canSend(Payload.TYPE);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean isHost() {
        return server() != null;
    }

    @Override
    public void sendToServer(byte[] payload) {
        try {
            if (ClientPlayNetworking.canSend(Payload.TYPE)) {
                ClientPlayNetworking.send(new Payload(encode(payload)));
            }
        } catch (Throwable t) {
            com.packhub.util.Log.warn("[PackHub] 发送同步消息失败: %s", t.toString());
        }
    }

    @Override
    public void broadcastToClients(byte[] payload) {
        MinecraftServer server = server();
        if (server == null) {
            return;
        }
        Payload packet = new Payload(encode(payload));
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player, packet);
            }
        });
    }

    @Override
    public void sendToPlayer(UUID playerId, byte[] payload) {
        MinecraftServer server = server();
        if (server == null) {
            return;
        }
        Payload packet = new Payload(encode(payload));
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.getUUID().equals(playerId)) {
                    ServerPlayNetworking.send(player, packet);
                    return;
                }
            }
        });
    }

    @Override
    public List<UUID> connectedPlayers() {
        List<UUID> ids = new ArrayList<>();
        MinecraftServer server = server();
        if (server == null) {
            return ids;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ids.add(player.getUUID());
        }
        return ids;
    }

    @Override
    public String playerName(UUID playerId) {
        MinecraftServer server = server();
        if (server == null) {
            return "?";
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(playerId)) {
                return player.getName().getString();
            }
        }
        return "?";
    }

    @Override
    public void setReceiver(Consumer<Incoming> receiver) {
        this.receiver = receiver == null ? incoming -> {
        } : receiver;
    }

    // ------------------------------------------------------------ 小工具

    private static MinecraftServer server() {
        try {
            return Minecraft.getInstance().getSingleplayerServer();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    private static byte[] decode(Payload payload) {
        try {
            return Base64.getDecoder().decode(payload.data());
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    private static Identifier createChannel() {
        for (String method : new String[]{"fromNamespaceAndPath", "of", "create"}) {
            try {
                Method factory = Identifier.class.getMethod(method, String.class, String.class);
                Object value = factory.invoke(null, NAMESPACE, PATH);
                if (value instanceof Identifier identifier) {
                    return identifier;
                }
            } catch (Throwable ignored) {
                // 试下一个方法名
            }
        }
        try {
            return Identifier.class.getConstructor(String.class, String.class).newInstance(NAMESPACE, PATH);
        } catch (Throwable t) {
            throw new IllegalStateException("无法创建 Identifier(" + NAMESPACE + ":" + PATH + ")", t);
        }
    }
}
