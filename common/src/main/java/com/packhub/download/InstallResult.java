package com.packhub.download;

import com.packhub.modrinth.ContentType;
import com.packhub.modrinth.model.ProjectSummary;
import com.packhub.modrinth.model.ProjectVersion;
import com.packhub.modrinth.model.VersionFile;

import java.nio.file.Path;

/** 一次安装的结果。 */
public final class InstallResult {
    public final ProjectSummary project;
    public final ProjectVersion version;
    public final VersionFile file;
    public final ContentType type;
    public final Path path;
    public final boolean hotReloaded;
    public final boolean needsRestart;
    public final String message;

    public InstallResult(ProjectSummary project, ProjectVersion version, VersionFile file, ContentType type,
                         Path path, boolean hotReloaded, boolean needsRestart, String message) {
        this.project = project;
        this.version = version;
        this.file = file;
        this.type = type;
        this.path = path;
        this.hotReloaded = hotReloaded;
        this.needsRestart = needsRestart;
        this.message = message;
    }

    public String title() {
        if (project != null && project.title != null && !project.title.isBlank()) {
            return project.title;
        }
        return file != null ? file.filename : "未知项目";
    }
}
