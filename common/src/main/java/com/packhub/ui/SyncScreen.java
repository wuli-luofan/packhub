package com.packhub.ui;

import com.packhub.PackHub;
import com.packhub.platform.NetworkBridge;
import com.packhub.sync.SyncManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 联机同步界面。
 * <p>房主：看到每个玩家的下载进度，全部就绪后一键重启（可配置自动）。</p>
 * <p>玩家：看到自己正在从房主那里下载什么，可以取消。</p>
 */
public class SyncScreen extends Screen {

    private final Screen parent;
    private Button broadcastButton;
    private Button restartButton;
    private Button cancelButton;

    public SyncScreen(Screen parent) {
        super(Component.literal("PackHub 联机同步"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        broadcastButton = addRenderableWidget(Button.builder(Component.literal("把模组同步给玩家"), button -> {
            int count = PackHub.sync().broadcast();
            Toasts.show("PackHub", count > 0 ? "已广播 " + count + " 个文件" : "没有可同步的模组（先用 PackHub 装几个）");
        }).bounds(10, height - 28, 150, 20).build());

        restartButton = addRenderableWidget(Button.builder(Component.literal("全部就绪，重启载入"), button -> {
            if (!PackHub.sync().allClientsReady()) {
                Toasts.show("PackHub", "还有玩家没装好，先等等");
                return;
            }
            PackHub.sync().restartEveryone();
        }).bounds(166, height - 28, 150, 20).build());

        cancelButton = addRenderableWidget(Button.builder(Component.literal("取消同步"), button -> {
            PackHub.sync().cancelBroadcast();
            Toasts.show("PackHub", "已取消本次同步");
        }).bounds(322, height - 28, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("打开 mods"), button ->
                ScreenUtil.openFolder(PackHub.platform().modsDir()))
                .bounds(428, height - 28, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("返回"), button -> onClose())
                .bounds(width - 84, height - 28, 74, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        NetworkBridge bridge = PackHub.platform().network();
        SyncManager sync = PackHub.sync();

        UiKit.header(graphics, Component.literal("PackHub · 联机模组同步"), width);
        graphics.text(font, "§7通道: " + (bridge.isAvailable() ? "可用" : "不可用（对面没装 PackHub 或未联机）")
                + " · 本机身份: " + (bridge.isHost() ? "房主/服务端" : "玩家"), 10, 30, 0xFFFFFFFF, false);

        if (bridge.isHost()) {
            graphics.text(font, "§b房主视图", 10, 46, 0xFFFFFFFF, false);
            List<SyncManager.ClientRow> clients = sync.clients();
            if (clients.isEmpty()) {
                graphics.text(font, "§7还没有玩家接入。玩家进服后会自动报到，或点下面的按钮广播清单。",
                        10, 60, 0xFFFFFFFF, false);
            }
            int y = 60;
            for (SyncManager.ClientRow row : clients) {
                graphics.fill(10, y, width - 10, y + 22, 0x30FFFFFF);
                graphics.text(font, row.name, 16, y + 3, 0xFFFFFFFF, false);
                int color = switch (row.state) {
                    case "READY" -> UiKit.COLOR_OK;
                    case "FAILED", "DECLINED" -> UiKit.COLOR_ERROR;
                    default -> UiKit.COLOR_ACCENT;
                };
                graphics.text(font, row.describe(), 16 + Math.max(80, font.width(row.name) + 12), y + 3, color, false);
                if (row.total > 0) {
                    int barX = width - 190;
                    graphics.fill(barX, y + 8, barX + 150, y + 12, 0xFF303030);
                    graphics.fill(barX, y + 8, barX + (int) (150f * Math.min(1f, row.completed / (float) row.total)),
                            y + 12, color);
                }
                y += 26;
            }
            graphics.text(font, "§7全部就绪后才能重启；重启会让所有已就绪的玩家一起重启以载入新模组。",
                    10, height - 44, 0xFFFFFFFF, false);
        } else {
            graphics.text(font, "§b玩家视图", 10, 46, 0xFFFFFFFF, false);
            graphics.text(font, "状态: " + sync.clientState(), 10, 62, 0xFFFFFFFF, false);
            graphics.text(font, "§7" + (sync.clientMessage().isBlank() ? "等待房主下发模组清单…" : sync.clientMessage()),
                    10, 76, 0xFFFFFFFF, false);
            if (sync.clientTotal() > 0) {
                float fraction = Math.min(1f, sync.clientIndex() / (float) sync.clientTotal());
                graphics.fill(10, 92, width - 10, 102, 0xFF202020);
                graphics.fill(10, 92, 10 + (int) ((width - 20) * fraction), 102, UiKit.COLOR_ACCENT);
                graphics.text(font, sync.clientIndex() + "/" + sync.clientTotal(), 12, 104, 0xFFFFFFFF, false);
            }
            graphics.text(font, "§7玩家侧：接受后自动下载到 mods/，装完全部就绪；房主重启时本机跟着一起重启。",
                    10, height - 44, 0xFFFFFFFF, false);
        }

        broadcastButton.visible = bridge.isHost();
        restartButton.visible = bridge.isHost();
        cancelButton.visible = bridge.isHost();
        restartButton.active = sync.allClientsReady();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        return super.mouseClicked(event, doubled);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
