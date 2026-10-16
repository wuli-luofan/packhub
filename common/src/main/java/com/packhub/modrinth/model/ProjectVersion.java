package com.packhub.modrinth.model;

import com.packhub.source.ContentSource;

import java.util.ArrayList;
import java.util.List;

/** 项目的一个版本（对应一个可下载文件集合）。 */
public final class ProjectVersion {
    /** 来源：决定后续依赖解析走哪个客户端。 */
    public ContentSource source = ContentSource.MODRINTH;
    public String id = "";
    public String projectId = "";
    public String name = "";
    public String versionNumber = "";
    public String versionType = "release";
    public String datePublished = "";
    public long downloads;
    public final List<String> gameVersions = new ArrayList<>();
    public final List<String> loaders = new ArrayList<>();
    public final List<VersionFile> files = new ArrayList<>();
    public final List<VersionDependency> dependencies = new ArrayList<>();

    public VersionFile primaryFile() {
        for (VersionFile file : files) {
            if (file.primary) {
                return file;
            }
        }
        return files.isEmpty() ? null : files.get(0);
    }

    /** 该版本是否可用于给定的游戏版本与加载器。 */
    public boolean supports(String gameVersion, String loader) {
        boolean gameOk = gameVersions.isEmpty() || gameVersions.contains(gameVersion);
        boolean loaderOk = loader == null || loaders.isEmpty() || loaders.contains(loader);
        return gameOk && loaderOk;
    }

    public String displayName() {
        String label = name != null && !name.isBlank() ? name : versionNumber;
        return label + "  [" + versionType + "]";
    }
}
