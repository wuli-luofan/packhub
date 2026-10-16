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
        // 必须用 bidirectional 只注册一次：NeoForge 的注册表按频道名去重，
        // 先 playToServer 再 playToClient 注册同一个 packhub:sync 会抛
        // UnsupportedOperationException: Cannot register payload packhub:sync as it is already registered
        // （这就是进游戏时 mod 加载失败的原因）。
        // 处理器里自己判断方向：服务端收到的是 C2S（context.player() 是 ServerPlayer），
        // 客户端收到的是 S2C；这样也不用关心重载参数顺序。
        try {
            registrar.playBidirectional(Payload.TYPE, Payload.CODEC, (payload, context) ->
                    context.enqueueWork(() -> handle(payload, context)));
        } catch (Throwable t) {
            // 注册失败只损失联机同步，绝不让整个 mod 加载失败
            com.packhub.util.Log.warn("[PackHub] 注册 %s 频道失败，联机同步不可用: %s",
                    Payload.TYPE.id(), t.toString());
        }
    }

    /** 按当前所处的一端分派：整合服务器与专用服务器都拿到 ServerPlayer，客户端拿到本地玩家。 */
    private static void handle(Payload payload, IPayloadContext context) {
        boolean serverSide = false;
        try {
            serverSide = context.player() instanceof ServerPlayer;
        } catch (Throwable ignored) {
            // 取不到就按客户端处理
        }
        if (serverSide) {
            dispatchFromClient(payload, context);
        } else {
            dispatchFromServer(payload);
        }
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

    // ------------------------------------------------------------ 发包
    //
    // 签名由 CI 的 javap 探测确认（NeoForge 26.3 的 PacketDistributor）：
    //   sendToServer(CustomPacketPayload, CustomPacketPayload...)
    //   sendToAllPlayers(CustomPacketPayload, CustomPacketPayload...)
    //   sendToPlayer(ServerPlayer, CustomPacketPayload, CustomPacketPayload...)
    // 都是可变参数形式；直接写调用会编译不过（编译期解析到的 PacketDistributor
    // 与探测到的不一致），所以按「方法名 + 可变参数」反射调用：
    // 签名对得上就执行，对不上只记一条警告，不会拖垮构建。

    private static boolean dispatch(String methodName, Object... fixedArgs) {
        try {
            Class<?> owner = Class.forName("net.neoforged.neoforge.network.PacketDistributor");
            for (Method candidate : owner.getMethods()) {
                if (!candidate.getName().equals(methodName) || !candidate.isVarArgs()) {
                    continue;
                }
                Class<?>[] types = candidate.getParameterTypes();
                if (fixedArgs.length != types.length - 1 || !compatible(types, fixedArgs)) {
                    continue;
                }
                Object[] callArgs = new Object[types.length];
                System.arraycopy(fixedArgs, 0, callArgs, 0, fixedArgs.length);
                callArgs[fixedArgs.length] = java.lang.reflect.Array.newInstance(
                        types[types.length - 1].getComponentType(), 0);
                candidate.invoke(null, callArgs);
                return true;
            }
            for (Method candidate : owner.getMethods()) {
                Class<?>[] types = candidate.getParameterTypes();
                if (!candidate.getName().equals(methodName) || types.length != fixedArgs.length
                        || !compatible(types, fixedArgs)) {
                    continue;
                }
                candidate.invoke(null, fixedArgs);
                return true;
            }
        } catch (Throwable t) {
            com.packhub.util.Log.warn("[PackHub] 反射发包失败(%s): %s", methodName, t.toString());
        }
        return false;
    }

    private static boolean compatible(Class<?>[] types, Object[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] != null && !types[i].isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void sendToServer(byte[] payload) {
        if (!dispatch("sendToServer", new Payload(encode(payload)))) {
            com.packhub.util.Log.warn("[PackHub] NeoForge 找不到发往服务端的入口，联机同步不可用");
        }
    }

    @Override
    public void broadcastToClients(byte[] payload) {
        MinecraftServer server = server();
        if (server == null) {
            return;
        }
        Payload packet = new Payload(encode(payload));
        server.execute(() -> dispatch("sendToAllPlayers", packet));
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
                    dispatch("sendToPlayer", player, packet);
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
