package com.packhub.ui;

import com.packhub.PackHub;
import com.packhub.config.PackHubConfig;
import com.packhub.source.ContentSource;
import com.packhub.util.Async;
import com.packhub.util.Log;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 设置界面：两个内容源的地址 / Key、开关、连通性测试。 */
public class SettingsScreen extends Screen {

    private final Screen parent;
    private final ListPanel<Setting> settings = new ListPanel<>();
    private EditBox apiBox;
    private EditBox cdnBox;
    private EditBox curseForgeKeyBox;
    private String status = "改动会立即保存到 config/packhub.json";

    private record Setting(String title, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
    }

    public SettingsScreen(Screen parent) {
        super(Component.literal("PackHub 设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PackHubConfig config = PackHub.config();

        apiBox = new EditBox(font, 10, 34, Math.max(80, width - 200), 20, Component.literal("Modrinth API 地址"));
        apiBox.setMaxLength(256);
        apiBox.setValue(config.apiBase);
        apiBox.setResponder(value -> {
            config.apiBase = value.trim();
            config.save();
            PackHub.syncSettings();
        });
        addRenderableWidget(apiBox);

        cdnBox = new EditBox(font, 10, 58, Math.max(80, width - 200), 20, Component.literal("Modrinth CDN 镜像"));
        cdnBox.setMaxLength(256);
        cdnBox.setValue(config.cdnBaseOverride);
        cdnBox.setResponder(value -> {
            config.cdnBaseOverride = value.trim();
            config.save();
            PackHub.syncSettings();
        });
        addRenderableWidget(cdnBox);

        curseForgeKeyBox = new EditBox(font, 10, 82, Math.max(80, width - 300), 20,
                Component.literal("CurseForge API Key"));
        curseForgeKeyBox.setMaxLength(128);
        curseForgeKeyBox.setValue(config.curseForgeApiKey);
        curseForgeKeyBox.setHint(Component.literal("粘贴 console.curseforge.com 申请的 API Key"));
        curseForgeKeyBox.setResponder(value -> {
            config.curseForgeApiKey = value.trim();
            config.save();
            PackHub.syncSettings();
        });
        addRenderableWidget(curseForgeKeyBox);

        addRenderableWidget(Button.builder(Component.literal("测试连接"), button -> testModrinth())
                .bounds(width - 184, 34, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("恢复默认"), button -> {
            config.apiBase = "https://api.modrinth.com/v2";
            config.cdnBaseOverride = "";
            apiBox.setValue(config.apiBase);
            cdnBox.setValue("");
            config.save();
            PackHub.syncSettings();
            status = "已恢复默认 Modrinth 地址";
        }).bounds(width - 96, 34, 86, 20).build());
        addRenderableWidget(Button.builder(Component.literal("打开配置目录"), button ->
                ScreenUtil.openFolder(PackHub.platform().configDir().resolve("packhub")))
                .bounds(width - 184, 58, 174, 20).build());

        addRenderableWidget(Button.builder(Component.literal("测试 CurseForge"), button -> testCurseForge())
                .bounds(width - 292, 82, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("申请 Key"), button ->
                ScreenUtil.openUrl("https://console.curseforge.com/"))
                .bounds(width - 184, 82, 174, 20).build());

        settings.bounds(10, 110, width - 20, Math.max(40, height - 110 - 32)).rowHeight(26);
        rebuild();

        addRenderableWidget(Button.builder(Component.literal("返回"), button -> onClose())
                .bounds(width - 84, height - 26, 74, 20).build());
    }

    private void rebuild() {
        settings.clear();
        PackHubConfig config = PackHub.config();
        List<Setting> values = new ArrayList<>();
        values.add(new Setting("默认来源切换", "搜索页「源」按钮的默认值，点击在 Modrinth / CurseForge 之间切换",
                () -> ContentSource.byId(config.preferredSource) == ContentSource.CURSEFORGE,
                value -> {
                    config.preferredSource = value ? "curseforge" : "modrinth";
                    PackHub.syncSettings();
                }));
        values.add(new Setting("启用 CurseForge", "关闭后搜索页会拒绝使用 CurseForge 来源",
                () -> config.enableCurseForge, value -> config.enableCurseForge = value));
        values.add(new Setting("自动安装必选前置", "下载模组时自动把 required 依赖一起装上（两个源都生效）",
                () -> config.autoResolveDependencies, value -> config.autoResolveDependencies = value));
        values.add(new Setting("安装后自动启用资源包", "下载完资源包后自动勾选并生效",
                () -> config.autoEnableResourcePacks, value -> config.autoEnableResourcePacks = value));
        values.add(new Setting("安装后自动热重载", "资源包 / 光影下载完成后立刻重载",
                () -> config.hotReloadAfterInstall, value -> config.hotReloadAfterInstall = value));
        values.add(new Setting("重载光影加载器", "尝试调用 Iris / Oculus 的重载入口",
                () -> config.reloadShaderLoader, value -> config.reloadShaderLoader = value));
        values.add(new Setting("插件热加载", "允许真正在运行时加载 packhub-plugin 格式的 jar",
                () -> config.enablePluginHotLoad, value -> config.enablePluginHotLoad = value));
        values.add(new Setting("允许模组软重启", "普通模组下载后可一键软重启载入",
                () -> config.allowSoftRestartForMods, value -> config.allowSoftRestartForMods = value));
        values.add(new Setting("自动软重启", "安装普通模组后自动重启进程（谨慎开启）",
                () -> config.autoSoftRestart, value -> config.autoSoftRestart = value));
        values.add(new Setting("监控 mods 目录", "外部手动放入 jar 时自动刷新列表",
                () -> config.watchModsFolder, value -> config.watchModsFolder = value));
        values.add(new Setting("按当前加载器过滤", "只显示适配 " + PackHub.platform().loaderName() + " 的模组",
                () -> config.filterByCurrentLoader, value -> config.filterByCurrentLoader = value));
        values.add(new Setting("备用 API 地址", "主地址失败时自动切换",
                () -> config.useFallbackWhenPrimaryFails, value -> config.useFallbackWhenPrimaryFails = value));
        values.add(new Setting("提示消息 (Toast)", "右上角弹出下载/安装结果",
                () -> config.showToasts, value -> config.showToasts = value));

        for (Setting setting : values) {
            ListPanel.Item<Setting> item = settings.add(setting);
            item.line(setting.title());
            item.line("§7" + setting.description());
            item.tag(setting.getter().getAsBoolean() ? "开" : "关");
            item.action(setting.getter().getAsBoolean() ? "关闭" : "开启", 48, value -> {
                value.setter().accept(!value.getter().getAsBoolean());
                PackHub.config().save();
                PackHub.syncSettings();
                rebuild();
            });
        }
    }

    private void testModrinth() {
        status = "正在测试 Modrinth…";
        Async.run(() -> {
            long start = System.currentTimeMillis();
            boolean ok = PackHub.modrinth().ping();
            return new Object[]{ok, System.currentTimeMillis() - start};
        }, result -> status = ((Boolean) result[0])
                ? "Modrinth 连接成功，耗时 " + result[1] + " ms"
                : "Modrinth 连接失败，请检查网络或改用镜像地址",
                error -> status = "Modrinth 连接失败: " + error.getMessage());
    }

    private void testCurseForge() {
        if (!PackHub.curseForge().usable()) {
            status = "请先填入 CurseForge API Key（https://console.curseforge.com/）";
            return;
        }
        status = "正在测试 CurseForge…";
        Async.run(() -> {
            long start = System.currentTimeMillis();
            boolean ok = PackHub.curseForge().ping();
            return new Object[]{ok, System.currentTimeMillis() - start};
        }, result -> status = ((Boolean) result[0])
                ? "CurseForge 连接成功，耗时 " + result[1] + " ms"
                : "CurseForge 连接失败：Key 无效 / 被限流 / 网络不可达",
                error -> {
                    status = "CurseForge 连接失败: " + error.getMessage();
                    Log.warn("[PackHub] CurseForge 测试失败: %s", error.toString());
                });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        UiKit.header(graphics, Component.literal("PackHub · 设置"), width);
        graphics.text(font, "§7Modrinth API: ", 10, 26, 0xFFFFFFFF, false);
        graphics.text(font, "§7Modrinth CDN 镜像: ", 10, 50, 0xFFFFFFFF, false);
        graphics.text(font, "§7CurseForge Key: ", 10, 74, 0xFFFFFFFF, false);
        graphics.text(font, "§7" + status, 10, 104, 0xFFFFFFFF, false);
        settings.render(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = UiKit.eventX(event);
        double mouseY = UiKit.eventY(event);
        int button = UiKit.eventButton(event);
if (settings.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (settings.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        PackHub.config().save();
        PackHub.syncSettings();
        minecraft.gui.setScreen(parent);
    }
}
