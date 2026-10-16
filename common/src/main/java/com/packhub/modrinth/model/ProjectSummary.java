package com.packhub.modrinth.model;

import com.packhub.modrinth.ContentType;
import com.packhub.source.ContentSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 搜索结果里的一个项目。 */
public final class ProjectSummary {
    /** 来源：Modrinth 或 CurseForge（两个源的结果共用这个模型）。 */
    public ContentSource source = ContentSource.MODRINTH;
    public String projectId = "";
    public String slug = "";
    public String title = "";
    public String description = "";
    public String author = "";
    public String iconUrl = "";
    public long downloads;
    public long follows;
    public String dateModified = "";
    public String latestVersion = "";
    public String license = "";
    public String clientSide = "unknown";
    public String serverSide = "unknown";
    public ContentType type = ContentType.MOD;
    public final List<String> categories = new ArrayList<>();
    public final List<String> versions = new ArrayList<>();
    public final List<String> loaders = new ArrayList<>();

    public String idOrSlug() {
        return projectId != null && !projectId.isEmpty() ? projectId : slug;
    }

    public String prettyDownloads() {
        if (downloads >= 1_000_000L) {
            return String.format("%.1fM", downloads / 1_000_000.0);
        }
        if (downloads >= 1_000L) {
            return String.format("%.1fK", downloads / 1_000.0);
        }
        return Long.toString(downloads);
    }

    public List<String> categoriesView() {
        return Collections.unmodifiableList(categories);
    }
}
