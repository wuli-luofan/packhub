package com.packhub.ui;

import com.packhub.PackHub;
import com.packhub.hotload.JarKind;
import com.packhub.hotload.ModJarInspector;
import com.packhub.hotload.ResourcePackReloader;
import com.packhub.hotload.plugin.LoadedPlugin;
import com.packhub.installed.InstalledModIndex;
import com.packhub.modrinth.ContentType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 已安装内容管理：启用 / 停用 / 删除 / 热重载。 */
public class InstalledScreen extends Screen {

    private enum Category { MOD, RESOURCEPACK, SHADER, PLUGIN }

    private final Screen parent;
    private final ListPanel<Row> rows = new ListPanel<>();
    private Category category = Category.MOD;
    private Button categoryButton;
    private String status = "";

    /** 列表行模型。 */
    private static final class Row {
        final Path path;
        final String title;
        final String subtitle;
        final String tag;
        final boolean enabled;
        final boolean plugin;
        final String pluginId;

        Row(Path path, String title, String subtitle, String tag, boolean enabled, boolean plugin, String pluginId) {
            this.path = path;
            this.title = title;
            this.subtitle = subtitle;
            this.tag = tag;
            this.enabled = enabled;
            this.plugin = plugin;
            this.pluginId = pluginId;
        }
    }

    public InstalledScreen(Screen parent) {
        super(Component.literal("PackHub 已安装"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int contentTop = 50;

        categoryButton = addRenderableWidget(Button.builder(categoryLabel(), button -> {
            Category[] values = Category.values();
            category = values[(category.ordinal() + 1) % values.length];
            button.setMessage(categoryLabel());
            refresh();
        }).bounds(10, contentTop - 24, 120, 20).build());

        int barHeight = new ButtonBar()
                .add("打开目录", 84, this::openFolder)
                .add("重载资源", 84, () -> {
                    PackHub.hotLoad().reloadNow();
                    status = "已触发资源重载";
                })
                .add("刷新", 60, this::refresh)
                .add("返回", 74, this::onClose)
                .rightAligned()
                .build(width, height, 6, this::addRenderableWidget);

        rows.bounds(10, contentTop, width - 20, Math.max(30, height - 10 - barHeight - contentTop));
        refresh();
    }

    private Component categoryLabel() {
        return Component.literal("分类: " + switch (category) {
            case MOD -> "模组";
            case RESOURCEPACK -> "资源包/材质";
            case SHADER -> "光影";
            case PLUGIN -> "热加载插件";
        });
    }

    private void refresh() {
        rows.clear();
        switch (category) {
            case MOD -> loadMods();
            case RESOURCEPACK -> loadPacks(PackHub.platform().gameDir().resolve("resourcepacks"), true);
            case SHADER -> loadPacks(PackHub.platform().gameDir().resolve("shaderpacks"), false);
            case PLUGIN -> loadPlugins();
        }
        status = "共 " + rows.size() + " 项";
    }

    private void loadMods() {
        for (InstalledModIndex.Entry entry : InstalledModIndex.scan(PackHub.platform())) {
            boolean enabled = entry.path.getParent() != null
                    && "mods".equals(entry.path.getParent().getFileName().toString());
            JarKind kind = entry.plugin ? JarKind.PACKHUB_PLUGIN : ModJarInspector.inspect(entry.path);
            Row row = new Row(entry.path, entry.displayName(),
                    "§7id: " + entry.modId + (entry.version == null ? "" : " · " + entry.version)
                            + " · " + HotLoadLabel.of(kind),
                    enabled ? "已启用" : "已停用", enabled, false, null);
            ListPanel.Item<Row> item = rows.add(row);
            item.line(row.title);
            item.line(row.subtitle);
            item.tag(row.tag);
            if (enabled) {
                item.action("停用", 44, value -> {
                    if (PackHub.hotLoad().disableMod(value.path)) {
                        status = "已停用 " + value.path.getFileName() + "（软重启后生效）";
                        refresh();
                    }
                });
            } else {
                item.action("启用", 44, value -> {
                    if (PackHub.hotLoad().enableMod(value.path)) {
                        status = "已启用 " + value.path.getFileName() + "（软重启后生效）";
                        refresh();
                    }
                });
            }
            item.action("删除", 44, value -> {
                PackHub.hotLoad().deleteMod(value.path);
                status = "已删除 " + value.path.getFileName();
                refresh();
            });
        }
    }

    private void loadPacks(Path dir, boolean resourcePack) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            List<Path> files = new ArrayList<>(stream.toList());
            files.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
            for (Path file : files) {
                String name = file.getFileName().toString();
                long size;
                try {
                    size = Files.size(file);
                } catch (Exception e) {
                    size = 0;
                }
                Row row = new Row(file, name, "§7" + UiKit.prettyBytes(size)
                        + (resourcePack ? " · 资源包" : " · 光影包"), resourcePack ? "资源包" : "光影",
                        true, false, null);
                ListPanel.Item<Row> item = rows.add(row);
                item.line(row.title);
                item.line(row.subtitle);
                item.tag(row.tag);
                if (resourcePack) {
                    item.action("启用", 44, value -> {
                        ResourcePackReloader.enablePack(value.path);
                        PackHub.hotLoad().scheduleResourceReload();
                        status = "已启用并热重载 " + value.path.getFileName();
                    });
                } else {
                    item.action("重载", 44, value -> {
                        PackHub.hotLoad().reloadNow();
                        status = "已重载光影列表";
                    });
                }
                item.action("删除", 44, value -> {
                    try {
                        Files.deleteIfExists(value.path);
                        status = "已删除 " + value.path.getFileName();
                    } catch (Exception e) {
                        status = "删除失败: " + e.getMessage();
                    }
                    refresh();
                });
            }
        } catch (Exception e) {
            status = "读取目录失败: " + e.getMessage();
        }
    }

    private void loadPlugins() {
        for (LoadedPlugin plugin : PackHub.hotLoad().plugins().plugins()) {
            Row row = new Row(plugin.jar(), plugin.manifest().displayName(),
                    "§7v" + plugin.manifest().version + " · " + plugin.manifest().id
                            + (plugin.manifest().description.isBlank() ? "" : " · " + plugin.manifest().description),
                    plugin.lastError().isBlank() ? "运行中" : "有错误", true, true, plugin.manifest().id);
            ListPanel.Item<Row> item = rows.add(row);
            item.line(row.title);
            item.line(row.subtitle);
            item.tag(row.tag);
            item.action("重载", 44, value -> {
                try {
                    PackHub.hotLoad().plugins().reload(value.pluginId);
                    status = "已重载插件 " + value.pluginId;
                } catch (Throwable t) {
                    status = "重载失败: " + t.getMessage();
                }
                refresh();
            });
            item.action("卸载", 44, value -> {
                PackHub.hotLoad().plugins().unload(value.pluginId);
                status = "已卸载插件 " + value.pluginId;
                refresh();
            });
        }
    }

    private void openFolder() {
        Path dir = switch (category) {
            case MOD -> PackHub.platform().modsDir();
            case RESOURCEPACK -> PackHub.platform().gameDir().resolve("resourcepacks");
            case SHADER -> PackHub.platform().gameDir().resolve("shaderpacks");
            case PLUGIN -> PackHub.hotLoad().plugins().pluginDir();
        };
        ScreenUtil.openFolder(dir);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        UiKit.header(graphics, Component.literal("PackHub · 已安装内容管理"), width);
        UiKit.drawTrimmed(graphics, "§7" + status, 12, 38, width - 140, 0xFFFFFFFF);
        rows.render(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = UiKit.eventX(event);
        double mouseY = UiKit.eventY(event);
        int button = UiKit.eventButton(event);
if (rows.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (rows.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    /** 把 jar 类型翻译成短标签。 */
    private static final class HotLoadLabel {
        static String of(JarKind kind) {
            return switch (kind) {
                case PACKHUB_PLUGIN -> "可热加载插件";
                case FABRIC_MOD -> "Fabric 模组";
                case NEOFORGE_MOD -> "NeoForge 模组";
                case UNKNOWN -> "未知 jar";
            };
        }
    }

    /** 供外部刷新用。 */
    public void reload() {
        refresh();
    }
}
