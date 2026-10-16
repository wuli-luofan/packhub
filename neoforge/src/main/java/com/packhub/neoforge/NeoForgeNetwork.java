package com.packhub.neoforge;

import com.packhub.platform.NetworkBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * NeoForge 侧的联机通道：用原版 Payload + NeoForge 的 PacketDistributor。
 * <p>数据同样包成 Base64 字符串，协议由 PackHub 自己版本化。</p>
 */
public final class NeoForgeNetwork implements NetworkBridge {

    private static final String NAMESPACE = "packhub";
    private static final String PATH = "sync";
    private static NeoForgeNetwork instance;

    private volatile Consumer<Incoming> receiver = incoming -> {
    };

    public static final Identifier CHANNEL = createChannel();

    public record Payload(String data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Payload> TYPE = new CustomPacketPayload.Type<>(CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Payload::data, Payload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static NeoForgeNetwork get() {
        if (instance == null) {
            instance = new NeoForgeNetwork();
        }
        return instance;
    }

    /** 注册数据包与处理器（在 mod 构造函数里挂到 mod 事件总线）。 */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(Payload.TYPE, Payload.CODEC, (payload, context) ->
                context.enqueueWork(() -> dispatchFromClient(payload, context)));
        registrar.playToClient(Payload.TYPE, Payload.CODEC, (payload, context) ->
                context.enqueueWork(() -> dispatchFromServer(payload)));
    }

    private static void dispatchFromClient(Payload payload, IPayloadContext context) {
        if (instance == null) {
            return;
        }
        UUID id = null;
        String name = "";
        try {
            if (context.player() instanceof ServerPlayer player) {
                id = player.getUUID();
                name = player.getName().getString();
            }
        } catch (Throwable ignored) {
            // 拿不到就用空值
        }
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
            return Minecraft.getInstance().getConnection() != null;
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
            PacketDistributor.sendToServer(new Payload(encode(payload)));
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
        server.execute(() -> PacketDistributor.sendToAllPlayers(packet));
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
                    PacketDistributor.sendToPlayer(player, packet);
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
