package com.packhub.ui;

import com.packhub.PackHub;
import com.packhub.modrinth.ContentType;
import com.packhub.modrinth.SearchQuery;
import com.packhub.modrinth.SearchResult;
import com.packhub.modrinth.model.ProjectSummary;
import com.packhub.modrinth.model.ProjectVersion;
import com.packhub.source.ContentSource;
import com.packhub.source.SourceException;
import com.packhub.util.Async;
import com.packhub.util.Log;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * PackHub 主界面：搜索并浏览 Modrinth / CurseForge 上的资源包 / 光影 / 模组 / 数据包。
 * <p>布局全部按当前界面尺寸动态计算：搜索框与内容区自适应，底部按钮栏在窄屏时自动换行，
 * 长文本会按宽度截断——解决「窗口小 / GUI 缩放很大时界面显示不全」的问题。</p>
 */
public class HubScreen extends Screen {

    private static final int SIDE = 10;

    private final Screen parent;

    private EditBox searchBox;
    private Button searchButton;
    private Button typeButton;
    private Button sourceButton;

    private final ListPanel<ProjectSummary> results = new ListPanel<>();
    private ContentType type = ContentType.RESOURCEPACK;
    private ContentSource source = ContentSource.MODRINTH;
    private int offset = 0;
    private int totalHits = 0;
    private boolean loading;
    private String status = "输入关键词后回车搜索；留空则列出热门内容";
    private int statusY = 50;

    public HubScreen(Screen parent) {
        super(Component.literal("PackHub"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        source = ContentSource.byId(PackHub.config().preferredSource);

        // ---------- 顶部：搜索框 + 三个功能按钮（窄屏时按钮换到第二行） ----------
        int topY = 26;
        int buttonsWidth = 50 + 92 + 108 + 8;
        boolean oneRow = width - SIDE * 2 - buttonsWidth >= 130;
        int searchWidth = oneRow ? width - SIDE * 2 - buttonsWidth : width - SIDE * 2;

        searchBox = new EditBox(font, SIDE, topY, Math.max(80, searchWidth), 20, Component.literal("搜索"));
        searchBox.setMaxLength(96);
        searchBox.setHint(Component.literal("搜索资源包 / 光影 / 模组…"));
        searchBox.setValue("");
        addRenderableWidget(searchBox);

        int buttonY = oneRow ? topY : topY + 22;
        int x = oneRow ? SIDE + searchWidth + 4 : SIDE;
        searchButton = addRenderableWidget(Button.builder(Component.literal("搜索"), button -> runSearch(0))
                .bounds(x, buttonY, 50, 20).build());
        x += 54;
        typeButton = addRenderableWidget(Button.builder(typeLabel(), button -> {
            ContentType[] values = {ContentType.RESOURCEPACK, ContentType.SHADER, ContentType.MOD, ContentType.DATAPACK};
            int index = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == type) {
                    index = i;
                }
            }
            type = values[(index + 1) % values.length];
            button.setMessage(typeLabel());
            runSearch(0);
        }).bounds(x, buttonY, 92, 20).build());
        x += 96;
        sourceButton = addRenderableWidget(Button.builder(sourceLabel(), button -> {
            source = source.next();
            PackHub.config().preferredSource = source.id();
            PackHub.config().save();
            button.setMessage(sourceLabel());
            runSearch(0);
        }).bounds(x, buttonY, 108, 20).build());

        int contentTop = buttonY + 26;
        statusY = contentTop - 12;

        // ---------- 底部按钮栏（自动换行 / 贴底） ----------
        ButtonBar bar = new ButtonBar()
                .add("上一页", 60, () -> offset > 0 && !loading, () -> {
                    offset = Math.max(0, offset - PackHub.config().searchPageSize);
                    runSearch(offset);
                })
                .add("下一页", 60, () -> (offset + PackHub.config().searchPageSize) < totalHits && !loading, () -> {
                    offset = offset + PackHub.config().searchPageSize;
                    runSearch(offset);
                })
                .add("联机", 54, () -> minecraft.gui.setScreen(new SyncScreen(this)))
                .add("进度", 54, () -> PackHub.openIfBusy(this))
                .add("已安装", 64, () -> minecraft.gui.setScreen(new InstalledScreen(this)))
                .add("热加载", 64, () -> minecraft.gui.setScreen(new HotLoadScreen(this)))
                .add("设置", 54, () -> minecraft.gui.setScreen(new SettingsScreen(this)))
                .add("关闭", 58, this::onClose)
                .rightAligned();
        int barHeight = bar.build(width, height, 6, this::addRenderableWidget);

        int contentBottom = height - 10 - barHeight;
        results.bounds(SIDE, contentTop, width - SIDE * 2, Math.max(30, contentBottom - contentTop));

        if (results.isEmpty() && !loading) {
            runSearch(0);
        }
    }

    private Component typeLabel() {
        return Component.literal("类型: " + typeName(type));
    }

    private Component sourceLabel() {
        return Component.literal("源: " + source.displayName());
    }

    private static String typeName(ContentType value) {
        return switch (value) {
            case RESOURCEPACK -> "资源包/材质";
            case SHADER -> "光影";
            case MOD -> "模组";
            case DATAPACK -> "数据包";
            case MODPACK -> "整合包";
        };
    }

    private void runSearch(int newOffset) {
        if (loading) {
            return;
        }
        if (source == ContentSource.CURSEFORGE && !PackHub.curseForge().usable()) {
            status = PackHub.config().enableCurseForge
                    ? "CurseForge 需要 API Key：打开「设置」填入 console.curseforge.com 申请的 Key"
                    : "CurseForge 已在设置中关闭";
            return;
        }
        loading = true;
        offset = Math.max(0, newOffset);
        status = "搜索中…";
        String query = searchBox == null ? "" : searchBox.getValue().trim();
        ContentType searchType = type;
        ContentSource searchSource = source;
        int pageSize = PackHub.config().searchPageSize;
        boolean filterLoader = PackHub.config().filterByCurrentLoader;
        String index = PackHub.config().searchIndex;
        String loader = PackHub.downloads().currentLoaderId();
        String gameVersion = PackHub.MINECRAFT_VERSION;

        Async.run(() -> {
            try {
                SearchQuery searchQuery = new SearchQuery()
                        .query(query)
                        .type(searchType)
                        .gameVersion(gameVersion)
                        .loader(loader)
                        .limit(pageSize)
                        .offset(offset)
                        .index(index);
                return searchSource == ContentSource.CURSEFORGE
                        ? PackHub.curseForge().search(searchQuery, filterLoader)
                        : PackHub.modrinth().search(searchQuery, filterLoader);
            } catch (SourceException e) {
                throw new RuntimeException(e);
            }
        }, this::applyResult, error -> {
            loading = false;
            status = "搜索失败: " + rootMessage(error);
            Log.warn("[PackHub] 搜索失败: %s", error.toString());
        });
    }

    private void applyResult(SearchResult result) {
        loading = false;
        totalHits = result.totalHits;
        results.clear();
        for (ProjectSummary hit : result.hits) {
            ListPanel.Item<ProjectSummary> item = results.add(hit);
            item.line(hit.title);
            item.line("§7" + hit.author + " · " + hit.prettyDownloads() + " 次下载 · " + typeName(hit.type)
                    + (hit.categories.isEmpty() ? "" : " · "
                    + String.join(",", hit.categories.subList(0, Math.min(3, hit.categories.size())))));
            item.tag(typeName(hit.type));
            item.action("版本", 44, this::openVersions);
            item.action("安装", 44, this::quickInstall);
        }
        status = result.totalHits == 0
                ? "没有找到匹配内容（可能是该版本 " + PackHub.MINECRAFT_VERSION + " 暂无对应资源）"
                : "共 " + result.totalHits + " 个结果，当前第 "
                + (offset / Math.max(1, PackHub.config().searchPageSize) + 1) + " 页";
        results.selected(null);
    }

    private void openVersions(ProjectSummary project) {
        minecraft.gui.setScreen(new VersionScreen(this, project, type));
    }

    private void quickInstall(ProjectSummary project) {
        status = "正在获取 " + project.title + " 的版本…";
        PackHub.downloads().fetchVersions(project, type).thenAccept(versions -> Async.onClient(() -> {
            if (versions.isEmpty()) {
                status = project.title + " 没有适配 " + PackHub.MINECRAFT_VERSION + " 的版本";
                return;
            }
            install(project, versions.get(0));
        }));
    }

    private void install(ProjectSummary project, ProjectVersion version) {
        if (version.primaryFile() == null) {
            status = "该版本没有可下载文件";
            return;
        }
        status = "开始下载 " + version.primaryFile().filename + "…";
        PackHub.openInstallProgress(this);
        PackHub.downloads().install(project, version, version.primaryFile(), type, true)
                .whenComplete((installResult, error) -> Async.onClient(() -> {
                    if (error != null) {
                        status = "安装失败: " + rootMessage(error);
                    } else {
                        status = installResult.message + " · " + installResult.path.getFileName();
                    }
                }));
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        UiKit.header(graphics, Component.literal("PackHub · 资源包 / 光影 / 模组 一键下载与热加载"), width);

        String line = loading ? "§e" + status : "§7" + status;
        UiKit.drawTrimmed(graphics, line, SIDE, Math.max(14, statusY), width - SIDE * 2, 0xFFFFFFFF);

        results.render(graphics, mouseX, mouseY);

        searchButton.active = !loading;
        typeButton.active = !loading;
        sourceButton.active = !loading;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = UiKit.eventX(event);
        double mouseY = UiKit.eventY(event);
        int button = UiKit.eventButton(event);
        if (results.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (results.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = UiKit.eventKey(event);
        if (keyCode == 257 || keyCode == 335) { // Enter / 小键盘 Enter
            runSearch(0);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
