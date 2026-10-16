package com.packhub.sync;

import com.packhub.PackHub;
import com.packhub.http.CancelToken;
import com.packhub.modrinth.ContentType;
import com.packhub.modrinth.model.VersionFile;
import com.packhub.platform.NetworkBridge;
import com.packhub.util.Log;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 联机模组同步的调度中心。
 *
 * <p>房主侧：把「自己用 PackHub 装过、且文件还在 mods/ 里」的模组整理成清单发给所有玩家，
 * 收集每个人的进度；等所有人都装好，房主这里给出「立即重启」的提示（可配置自动重启）。</p>
 *
 * <p>玩家侧：收到清单后自动下载到自己的 mods/，逐步回报进度，装好后回报 READY；
 * 房主按 RESTART 时跟着一起软重启。</p>
 */
public class SyncManager {

    /** 房主视角里的一个玩家。 */
    public static final class ClientRow {
        public final UUID id;
        public final String name;
        public String state = "WAITING";
        public int completed;
        public int total;
        public String message = "";

        ClientRow(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public String describe() {
            return switch (state) {
                case "DOWNLOADING" -> "下载中 " + completed + "/" + total;
                case "READY" -> "已就绪";
                case "FAILED" -> "失败: " + message;
                case "DECLINED" -> "已拒绝";
                default -> "等待中";
            };
        }
    }

    private final Map<UUID, ClientRow> clients = new LinkedHashMap<>();
    private volatile SyncManifest currentManifest;
    private volatile boolean clientSyncing;
    private volatile String clientState = "IDLE";
    private volatile String clientMessage = "";
    private volatile int clientIndex;
    private volatile int clientTotal;
    private final CancelToken clientToken = new CancelToken();

    // ------------------------------------------------------------ 生命周期

    /** 由 PackHub 初始化时调用：挂上网络收发。 */
    public void start() {
        NetworkBridge bridge = PackHub.platform().network();
        bridge.setReceiver(this::onIncoming);
        Log.info("[PackHub] 联机同步已就绪（通道可用=%s，本机是房主=%s）", bridge.isAvailable(), bridge.isHost());
    }

    public void stop() {
        clients.clear();
        clientToken.cancel("退出游戏");
    }

    /** 客户端 tick：用于在下载过程中把状态同步给界面（这里不需要做别的）。 */
    public void tick() {
        // 目前无需轮询；保留钩子以便以后加超时/重试。
    }

    // ------------------------------------------------------------ 房主侧

    public boolean hostActive() {
        return currentManifest != null && !currentManifest.isEmpty();
    }

    public SyncManifest currentManifest() {
        return currentManifest;
    }

    public List<ClientRow> clients() {
        return new ArrayList<>(clients.values());
    }

    /**
     * 生成清单并广播给所有玩家。
     *
     * @return 清单里的文件数（0 表示没有需要同步的东西）
     */
    public int broadcast() {
        NetworkBridge bridge = PackHub.platform().network();
        if (!bridge.isAvailable() || !bridge.isHost()) {
            Log.warn("[PackHub] 当前不是房主或通道不可用，跳过同步广播");
            return 0;
        }
        SyncManifest manifest = SyncManifest.fromRegistry(
                localPlayerName(),
                PackHub.downloads().registry(),
                PackHub.platform().modsDir(),
                PackHub.config().syncResourcePacks,
                PackHub.config().syncMaxFileSizeMb);
        currentManifest = manifest;
        clients.clear();
        for (UUID id : bridge.connectedPlayers()) {
            clients.put(id, new ClientRow(id, bridge.playerName(id)));
        }
        if (manifest.isEmpty()) {
            Log.info("[PackHub] 没有可同步的模组");
            return 0;
        }
        bridge.broadcastToClients(SyncMessage.manifest(manifest).encode());
        Log.info("[PackHub] 已向 %d 名玩家广播 %d 个文件", clients.size(), manifest.size());
        return manifest.size();
    }

    /** 是否所有玩家都已就绪（至少要有 1 个玩家）。 */
    public boolean allClientsReady() {
        if (clients.isEmpty()) {
            return false;
        }
        for (ClientRow row : clients.values()) {
            if (!"READY".equals(row.state)) {
                return false;
            }
        }
        return true;
    }

    public boolean anyClientActive() {
        for (ClientRow row : clients.values()) {
            if ("DOWNLOADING".equals(row.state) || "WAITING".equals(row.state)) {
                return true;
            }
        }
        return false;
    }

    /** 所有人就绪后由房主发起的重启：先通知客户端，再重启自己。 */
    public void restartEveryone() {
        NetworkBridge bridge = PackHub.platform().network();
        if (bridge.isAvailable()) {
            bridge.broadcastToClients(new SyncMessage(SyncMessage.Kind.RESTART).encode());
        }
        Log.info("[PackHub] 全部玩家已就绪，房主开始重启");
        PackHub.hotLoad().softRestart();
    }

    /** 取消本次同步（房主侧）。 */
    public void cancelBroadcast() {
        NetworkBridge bridge = PackHub.platform().network();
        if (bridge.isAvailable()) {
            bridge.broadcastToClients(new SyncMessage(SyncMessage.Kind.CANCEL).encode());
        }
        currentManifest = null;
        clients.clear();
    }

    // ------------------------------------------------------------ 玩家侧

    public boolean clientSyncing() {
        return clientSyncing;
    }

    public String clientState() {
        return clientState;
    }

    public String clientMessage() {
        return clientMessage;
    }

    public int clientIndex() {
        return clientIndex;
    }

    public int clientTotal() {
        return clientTotal;
    }

    /** 玩家侧取消（用安装进度界面的取消按钮调用）。 */
    public void clientCancel() {
        clientToken.cancel("玩家取消同步");
        clientState = "CANCELLED";
        clientMessage = "已取消，未下载完成的部分不会生效";
        sendToServer(SyncMessage.withText(SyncMessage.Kind.DECLINE, "玩家取消"));
    }

    // ------------------------------------------------------------ 消息处理

    private void onIncoming(NetworkBridge.Incoming incoming) {
        SyncMessage message;
        try {
            message = SyncMessage.decode(incoming.data());
        } catch (Throwable t) {
            Log.warn("[PackHub] 无法解析同步消息: %s", t.toString());
            return;
        }
        NetworkBridge bridge = PackHub.platform().network();
        if (incoming.fromServer()) {
            // 客户端收到房主的消息
            switch (message.kind) {
                case MANIFEST -> handleManifest(bridge, message.manifest);
                case RESTART -> handleRestartCommand();
                case CANCEL -> {
                    clientToken.cancel("房主取消同步");
                    clientState = "CANCELLED";
                    clientMessage = "房主取消了本次同步";
                }
                default -> {
                }
            }
        } else {
            // 房主收到玩家的消息
            UUID sender = incoming.sender();
            ClientRow row = clients.computeIfAbsent(sender,
                    id -> new ClientRow(id, incoming.senderName() == null ? bridge.playerName(id) : incoming.senderName()));
            switch (message.kind) {
                case HELLO -> {
                    row.state = "WAITING";
                    if (currentManifest != null && !currentManifest.isEmpty()) {
                        bridge.sendToPlayer(sender, SyncMessage.manifest(currentManifest).encode());
                    }
                }
                case PROGRESS -> {
                    row.state = "DOWNLOADING";
                    row.completed = message.index;
                    row.total = message.total;
                    row.message = message.text;
                }
                case READY -> {
                    row.state = "READY";
                    row.completed = row.total;
                    Log.info("[PackHub] 玩家 %s 已就绪", row.name);
                    if (allClientsReady() && PackHub.config().syncAutoRestart) {
                        restartEveryone();
                    }
                }
                case FAILED -> {
                    row.state = "FAILED";
                    row.message = message.text;
                }
                case DECLINE -> {
                    row.state = "DECLINED";
                    row.message = message.text;
                }
                default -> {
                }
            }
        }
    }

    private void handleManifest(NetworkBridge bridge, SyncManifest manifest) {
        if (manifest == null || manifest.isEmpty()) {
            return;
        }
        if (!PackHub.config().allowServerModPush) {
            sendToServer(SyncMessage.withText(SyncMessage.Kind.DECLINE, "本机已关闭「允许服务器下发模组」"));
            clientState = "DECLINED";
            clientMessage = "设置里关闭了接收服务器模组";
            return;
        }
        if (clientSyncing) {
            Log.info("[PackHub] 忽略重复的同步清单（已有同步在进行）");
            return;
        }
        clientSyncing = true;
        clientState = "DOWNLOADING";
        clientIndex = 0;
        clientTotal = manifest.size();
        clientMessage = "来自房主 " + manifest.hostName + " 的 " + manifest.size() + " 个文件";
        sendToServer(SyncMessage.withText(SyncMessage.Kind.ACCEPT, "开始下载"));
        com.packhub.ui.Toasts.show("房主同步模组", clientMessage);

        Path modsDir = PackHub.platform().modsDir();
        SyncDownloader downloader = new SyncDownloader(modsDir, (entry, target, listener, token) -> {
            VersionFile file = new VersionFile();
            file.url = entry.url;
            file.filename = entry.fileName;
            file.sha1 = entry.sha1;
            file.size = entry.size;
            return PackHub.downloads().downloadFile(com.packhub.source.ContentSource.MODRINTH,
                    file, target, listener, token);
        });

        Thread worker = new Thread(() -> {
            SyncDownloader.Outcome outcome = downloader.run(manifest, clientToken,
                    status -> clientMessage = status,
                    (index, total) -> {
                        clientIndex = index;
                        clientTotal = total;
                        sendToServer(SyncMessage.progress(clientMessage, index, total));
                    });
            clientSyncing = false;
            if (outcome.cancelled) {
                clientState = "CANCELLED";
                sendToServer(SyncMessage.withText(SyncMessage.Kind.DECLINE, "已取消"));
                return;
            }
            if (outcome.allOk()) {
                clientState = "READY";
                clientMessage = outcome.summary() + " · 等待房主重启";
                sendToServer(SyncMessage.withText(SyncMessage.Kind.READY, outcome.summary()));
                com.packhub.ui.Toasts.show("同步完成", "已装好房主的模组，等房主重启后一起生效");
            } else {
                clientState = "FAILED";
                clientMessage = String.join("；", outcome.failures);
                sendToServer(SyncMessage.withText(SyncMessage.Kind.FAILED, clientMessage));
                com.packhub.ui.Toasts.error("同步失败", clientMessage);
            }
        }, "PackHub-Sync");
        worker.setDaemon(true);
        worker.start();
    }

    private void handleRestartCommand() {
        if (!PackHub.config().allowServerModPush) {
            return;
        }
        Log.info("[PackHub] 房主要求重启以载入同步下来的模组");
        com.packhub.ui.Toasts.show("PackHub", "房主已重启，本机也跟着重启以载入新模组");
        runOnClientThread(() -> PackHub.hotLoad().softRestart());
    }

    private void sendToServer(SyncMessage message) {
        NetworkBridge bridge = PackHub.platform().network();
        if (bridge.isAvailable()) {
            bridge.sendToServer(message.encode());
        }
    }

    // ------------------------------------------------------------ 小工具

    private static String localPlayerName() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                return minecraft.player.getName().getString();
            }
        } catch (Throwable ignored) {
            // 落到默认值
        }
        return "房主";
    }

    private static void runOnClientThread(Runnable action) {
        try {
            Minecraft.getInstance().execute(action);
        } catch (Throwable t) {
            action.run();
        }
    }

    /** 供界面显示的来源类型（同步下来的都是模组或资源包）。 */
    public static ContentType typeOf(SyncEntry entry) {
        try {
            return ContentType.valueOf(entry.type);
        } catch (RuntimeException e) {
            return ContentType.MOD;
        }
    }
}
