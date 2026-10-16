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
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * PackHub 主界面：搜索并浏览 Modrinth 上的资源包 / 光影 / 模组 / 数据包。
 */
public class HubScreen extends Screen {

    private final Screen parent;

    private EditBox searchBox;
    private Button searchButton;
    private Button typeButton;
    private Button sourceButton;
    private Button prevButton;
    private Button nextButton;

    private final ListPanel<ProjectSummary> results = new ListPanel<>();
    private ContentType type = ContentType.RESOURCEPACK;
    private ContentSource source = ContentSource.MODRINTH;
    private int offset = 0;
    private int totalHits = 0;
    private boolean loading;
    private String status = "输入关键词后回车搜索；留空则列出热门内容";

    public HubScreen(Screen parent) {
        super(Component.literal("PackHub"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        source = ContentSource.byId(PackHub.config().preferredSource);
        int centerX = width / 2;
        int searchWidth = Math.min(240, Math.max(120, width / 2 - 60));
        searchBox = new EditBox(font, centerX - searchWidth, 28, searchWidth, 20, Component.literal("搜索"));
        searchBox.setMaxLength(96);
        searchBox.setHint(Component.literal("搜索资源包 / 光影 / 模组…"));
        searchBox.setValue("");
        addRenderableWidget(searchBox);

        searchButton = addRenderableWidget(Button.builder(Component.literal("搜索"), button -> runSearch(0))
                .bounds(centerX + 2, 28, 50, 20).build());
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
        }).bounds(centerX + 56, 28, 92, 20).build());
        sourceButton = addRenderableWidget(Button.builder(sourceLabel(), button -> {
            source = source.next();
            PackHub.config().preferredSource = source.id();
            PackHub.config().save();
            button.setMessage(sourceLabel());
            runSearch(0);
        }).bounds(centerX + 152, 28, 108, 20).build());

        int listTop = 56;
        int listBottom = height - 34;
        results.bounds(10, listTop, width - 20, Math.max(40, listBottom - listTop));

        prevButton = addRenderableWidget(Button.builder(Component.literal("上一页"), button -> {
            offset = Math.max(0, offset - PackHub.config().searchPageSize);
            runSearch(offset);
        }).bounds(10, height - 28, 60, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal("下一页"), button -> {
            offset = offset + PackHub.config().searchPageSize;
            runSearch(offset);
        }).bounds(74, height - 28, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("已安装"), button ->
                minecraft.gui.setScreen(new InstalledScreen(this))).bounds(width - 250, height - 28, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("热加载"), button ->
                minecraft.gui.setScreen(new HotLoadScreen(this))).bounds(width - 186, height - 28, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("设置"), button ->
                minecraft.gui.setScreen(new SettingsScreen(this))).bounds(width - 122, height - 28, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), button -> onClose())
                .bounds(width - 68, height - 28, 58, 20).build());

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
            if (!PackHub.config().enableCurseForge) {
                status = "CurseForge 已在设置中关闭";
            } else {
                status = "CurseForge 需要 API Key：打开「设置」填入 console.curseforge.com 申请的 Key";
            }
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
                    + (hit.categories.isEmpty() ? "" : " · " + String.join(",", hit.categories.subList(0, Math.min(3, hit.categories.size())))));
            item.tag(typeName(hit.type));
            item.action("版本", 44, this::openVersions);
            item.action("安装", 44, this::quickInstall);
        }
        status = result.totalHits == 0
                ? "没有找到匹配内容（可能是该版本 " + PackHub.MINECRAFT_VERSION + " 暂无对应资源）"
                : "共 " + result.totalHits + " 个结果，当前第 " + (offset / Math.max(1, PackHub.config().searchPageSize) + 1) + " 页";
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
            ProjectVersion version = versions.get(0);
            install(project, version);
        }));
    }

    private void install(ProjectSummary project, ProjectVersion version) {
        if (version.primaryFile() == null) {
            status = "该版本没有可下载文件";
            return;
        }
        status = "开始下载 " + version.primaryFile().filename + "…";
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
        graphics.text(font, "§7" + status, 12, 50, 0xFFFFFFFF, false);
        results.render(graphics, mouseX, mouseY);
        if (loading) {
            graphics.text(font, "§e加载中…", width - 60, 50, 0xFFFFFFFF, false);
        }
        prevButton.active = offset > 0 && !loading;
        nextButton.active = (offset + PackHub.config().searchPageSize) < totalHits && !loading;
        searchButton.active = !loading;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (results.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (results.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / 小键盘 Enter
            runSearch(0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
