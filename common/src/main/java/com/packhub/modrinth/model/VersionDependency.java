package com.packhub.modrinth.model;

/** 版本依赖。 */
public final class VersionDependency {
    public String versionId = "";
    public String projectId = "";
    public String fileName = "";
    /** required / optional / incompatible / embedded */
    public String dependencyType = "required";

    public boolean isRequired() {
        return "required".equalsIgnoreCase(dependencyType);
    }
}
