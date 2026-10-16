package com.packhub.ui;

import com.packhub.PackHub;
import com.packhub.modrinth.ContentType;
import com.packhub.modrinth.model.ProjectSummary;
import com.packhub.modrinth.model.ProjectVersion;
import com.packhub.modrinth.model.VersionFile;
import com.packhub.source.ContentSource;
import com.packhub.util.Async;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 选择具体版本并安装。 */
public class VersionScreen extends Screen {

    private final Screen parent;
    private final ProjectSummary project;
    private final ContentType type;
    private final ListPanel<ProjectVersion> versions = new ListPanel<>();

    private boolean autoEnable = true;
    private boolean loading = true;
    private String status = "正在获取版本列表…";
    private Button enableButton;
    private Button dependencyButton;
    private Button installButton;
    private ButtonBar bar;

    public VersionScreen(Screen parent, ProjectSummary project, ContentType type) {
        super(Component.literal("PackHub 版本选择"));
        this.parent = parent;
        this.project = project;
        this.type = type;
    }

    @Override
    protected void init() {
        int listTop = 50;

        // 底部按钮栏（窄屏自动换行）
        bar = new ButtonBar()
                .add("安装选中版本", 110, () -> versions.selected() != null && !loading, this::installSelected)
                .add("打开项目页", 100, () -> ScreenUtil.openUrl(projectPageUrl()))
                .add("返回", 74, this::onClose)
                .rightAligned();
        int barHeight = bar.build(width, height, 6, this::addRenderableWidget);

        // 两个开关放在按钮栏上方一行，宽度按屏幕自适应
        int toggleWidth = Math.max(70, Math.min(150, (width - 30) / 2));
        int toggleY = height - 10 - barHeight - 26;
        enableButton = addRenderableWidget(Button.builder(enableLabel(), button -> {
            autoEnable = !autoEnable;
            button.setMessage(enableLabel());
        }).bounds(10, toggleY, toggleWidth, 20).build());

        dependencyButton = addRenderableWidget(Button.builder(dependencyLabel(), button -> {
            PackHub.config().autoResolveDependencies = !PackHub.config().autoResolveDependencies;
            PackHub.config().save();
            button.setMessage(dependencyLabel());
        }).bounds(14 + toggleWidth, toggleY, toggleWidth, 20).build());

        installButton = null;

        int contentBottom = toggleY - 6;
        versions.bounds(10, listTop, width - 20, Math.max(30, contentBottom - listTop));

        versions.selected(null);
        load();
    }

    private Component enableLabel() {
        return Component.literal(type == ContentType.RESOURCEPACK
                ? "安装后自动启用: " + (autoEnable ? "开" : "关")
                : "安装后热重载: " + (autoEnable ? "开" : "关"));
    }

    private Component dependencyLabel() {
        return Component.literal("自动装前置: " + (PackHub.config().autoResolveDependencies ? "开" : "关"));
    }

    private void load() {
        loading = true;
        PackHub.downloads().fetchVersions(project, type).thenAccept(list -> Async.onClient(() -> {
            loading = false;
            apply(list);
        }));
    }

    private void apply(List<ProjectVersion> list) {
        versions.clear();
        if (list.isEmpty()) {
            status = "该项目没有适配 " + PackHub.MINECRAFT_VERSION + " 的版本";
            return;
        }
        for (ProjectVersion version : list) {
            VersionFile file = version.primaryFile();
            ListPanel.Item<ProjectVersion> item = versions.add(version);
            item.line(version.displayName());
            item.line("§7" + String.join(", ", version.gameVersions) + " · "
                    + String.join(", ", version.loaders)
                    + (file != null ? " · " + file.filename + " (" + file.prettySize() + ")" : " · 无文件"));
            item.tag(version.versionType);
            boolean compatible = version.supports(PackHub.MINECRAFT_VERSION, loaderId());
            item.action("安装", 44, value -> compatible, this::install);
        }
        status = "共 " + list.size() + " 个版本（优先显示适配 " + PackHub.MINECRAFT_VERSION + " 的版本）";
        ProjectVersion first = versions.items().isEmpty() ? null : versions.items().get(0).value;
        versions.selected(first);
    }

    private String loaderId() {
        return PackHub.downloads().currentLoaderId();
    }

    /** 按来源拼项目主页：Modrinth 与 CurseForge 的路径规则完全不同。 */
    private String projectPageUrl() {
        String slug = project.slug != null && !project.slug.isBlank() ? project.slug : project.idOrSlug();
        if (project.source == ContentSource.CURSEFORGE) {
            String section = switch (type) {
                case RESOURCEPACK -> "texture-packs";
                case SHADER -> "shaders";
                case DATAPACK -> "data-packs";
                case MODPACK -> "modpacks";
                case MOD -> "mc-mods";
            };
            return "https://www.curseforge.com/minecraft/" + section + "/" + slug;
        }
        return "https://modrinth.com/project/" + slug;
    }

    private void installSelected() {
        install(versions.selected());
    }

    private void install(ProjectVersion version) {
        if (version == null) {
            status = "请先点击列表中的版本";
            return;
        }
        VersionFile file = version.primaryFile();
        if (file == null) {
            status = "该版本没有可下载文件";
            return;
        }
        status = "开始下载 " + file.filename + "（" + file.prettySize() + "）…";
        PackHub.openInstallProgress(this);
        PackHub.downloads().install(project, version, file, type, autoEnable)
                .whenComplete((result, error) -> Async.onClient(() -> {
                    if (error != null) {
                        status = "安装失败: " + error.getMessage();
                    } else {
                        status = result.message + " -> " + result.path.getFileName();
                    }
                }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        UiKit.header(graphics, Component.literal("PackHub · " + project.title + "  [" + project.source.displayName() + "]"), width);
        UiKit.drawTrimmed(graphics, "§7" + status, 12, 38, width - 24, 0xFFFFFFFF);
        if (loading) {
            graphics.text(font, "§e加载中…", 12, 26, 0xFFFFFFFF, false);
        }
        versions.render(graphics, mouseX, mouseY);
        if (bar != null) {
            bar.refresh();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = UiKit.eventX(event);
        double mouseY = UiKit.eventY(event);
        int button = UiKit.eventButton(event);
if (versions.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (versions.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
