package com.packhub.ui;

import com.packhub.PackHub;
import com.packhub.hotload.GameRestarter;
import com.packhub.hotload.JarKind;
import com.packhub.hotload.ModJarInspector;
import com.packhub.hotload.plugin.LoadedPlugin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 热加载控制台：
 * <ul>
 *     <li>资源包 / 光影 / 数据包 —— 真正热重载；</li>
 *     <li>PackHub 插件 —— 运行时加载 / 卸载 / 重载；</li>
 *     <li>普通模组 —— 进待重启队列，一键软重启完成“热加载”。</li>
 * </ul>
 */
public class HotLoadScreen extends Screen {

    private final Screen parent;
    private final ListPanel<String> pending = new ListPanel<>();
    private final ListPanel<LoadedPlugin> plugins = new ListPanel<>();
    private String status = "";
    private Button restartButton;
    private Button reloadButton;

    public HotLoadScreen(Screen parent) {
        super(Component.literal("PackHub 热加载"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int half = (width - 30) / 2;
        pending.bounds(10, 74, half, Math.max(40, height - 74 - 62)).rowHeight(26);
        plugins.bounds(20 + half, 74, half, Math.max(40, height - 74 - 62)).rowHeight(34);

        restartButton = addRenderableWidget(Button.builder(Component.literal("一键软重启并载入"), button -> {
            status = "正在软重启，新进程会用同样的命令行启动…";
            boolean started = PackHub.hotLoad().softRestart();
            if (!started) {
                status = "软重启失败；已尝试生成 restart-minecraft.bat 到 config/packhub/";
            }
        }).bounds(10, height - 54, 150, 20).build());

        reloadButton = addRenderableWidget(Button.builder(Component.literal("立即热重载资源"), button -> {
            PackHub.hotLoad().reloadNow();
            status = "已触发热重载（资源包 / 光影 / 插件回调）";
        }).bounds(166, height - 54, 140, 20).build());

        addRenderableWidget(Button.builder(Component.literal("打开插件目录"), button ->
                ScreenUtil.openFolder(PackHub.hotLoad().plugins().pluginDir()))
                .bounds(312, height - 54, 110, 20).build());

        addRenderableWidget(Button.builder(Component.literal("打开 mods"), button ->
                ScreenUtil.openFolder(PackHub.platform().modsDir()))
                .bounds(428, height - 54, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("复制重启命令"), button -> {
            ScreenUtil.copyToClipboard(GameRestarter.describeCommand());
            status = "已复制重启命令到剪贴板";
        }).bounds(534, height - 54, 110, 20).build());

        addRenderableWidget(Button.builder(Component.literal("刷新"), button -> refresh())
                .bounds(width - 150, height - 54, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), button -> onClose())
                .bounds(width - 84, height - 54, 74, 20).build());

        refresh();
    }

    private void refresh() {
        List<String> pendingList = PackHub.hotLoad().pendingList();
        pending.clear();
        for (String name : pendingList) {
            Path path = PackHub.platform().modsDir().resolve(name);
            JarKind kind = Files.isRegularFile(path) ? ModJarInspector.inspect(path) : JarKind.UNKNOWN;
            ListPanel.Item<String> item = pending.add(name);
            item.line(name);
            item.line("§7" + describe(kind) + " · 重启后载入");
            item.tag("待生效");
        }

        plugins.clear();
        for (LoadedPlugin plugin : PackHub.hotLoad().plugins().plugins()) {
            ListPanel.Item<LoadedPlugin> item = plugins.add(plugin);
            item.line(plugin.manifest().displayName() + " §7v" + plugin.manifest().version);
            String statusLine = plugin.statusLine();
            item.line("§7" + plugin.manifest().id
                    + (statusLine.isBlank() ? "" : " · " + statusLine)
                    + (plugin.lastError().isBlank() ? "" : " §c" + plugin.lastError()));
            item.tag(plugin.lastError().isBlank() ? "运行中" : "异常");
            item.action("重载", 44, value -> {
                try {
                    PackHub.hotLoad().plugins().reload(value.manifest().id);
                    status = "插件已重载: " + value.manifest().id;
                } catch (Throwable t) {
                    status = "重载失败: " + t.getMessage();
                }
                refresh();
            });
            item.action("卸载", 44, value -> {
                PackHub.hotLoad().plugins().unload(value.manifest().id);
                status = "插件已卸载: " + value.manifest().id;
                refresh();
            });
        }

        if (pendingList.isEmpty() && plugins.isEmpty()) {
            status = "暂无待生效模组，也没有加载中的插件。下载一个 packhub-plugin 格式的 jar 即可看到真正的运行时热加载。";
        } else if (!pendingList.isEmpty()) {
            status = "有 " + pendingList.size() + " 个普通模组等待重启后生效（JVM 限制：普通模组无法在进程内卸载）。";
        }
    }

    private static String describe(JarKind kind) {
        return switch (kind) {
            case PACKHUB_PLUGIN -> "可热加载插件（也可能需要重启后注册）";
            case FABRIC_MOD -> "Fabric 模组";
            case NEOFORGE_MOD -> "NeoForge 模组";
            case UNKNOWN -> "未知 jar";
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        UiKit.header(graphics, Component.literal("PackHub · 热加载控制台"), width);
        graphics.text(font, "§7" + PackHub.hotLoad().backendSummary()
                + " · 运行环境 " + PackHub.MINECRAFT_VERSION, 12, 30, 0xFFFFFFFF, false);
        graphics.text(font, "§7" + status, 12, 42, 0xFFFFFFFF, false);
        graphics.text(font, "§b待重启生效的模组", 12, 62, 0xFFFFFFFF, false);
        graphics.text(font, "§b运行中的热加载插件", 20 + (width - 30) / 2, 62, 0xFFFFFFFF, false);
        graphics.text(font, "§8说明：资源包/光影/数据包可真正热重载；普通 Fabric/NeoForge 模组受 JVM 与 Mixin 限制必须重启进程，"
                + "PackHub 用“软重启”自动完成这一步。", 10, height - 26, 0xFFFFFFFF, false);
        pending.render(graphics, mouseX, mouseY);
        plugins.render(graphics, mouseX, mouseY);
        restartButton.active = !PackHub.hotLoad().pendingList().isEmpty();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pending.mouseClicked(mouseX, mouseY, button) || plugins.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pending.mouseScrolled(mouseX, mouseY, scrollY) || plugins.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
