package com.packhub.modrinth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.packhub.http.Http;
import com.packhub.http.ProgressListener;
import com.packhub.modrinth.model.ProjectSummary;
import com.packhub.modrinth.model.ProjectVersion;
import com.packhub.modrinth.model.VersionDependency;
import com.packhub.modrinth.model.VersionFile;
import com.packhub.source.ContentSource;
import com.packhub.source.SourceException;
import com.packhub.util.Log;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Modrinth v2 API 客户端。纯 java.net + Gson，不依赖 Minecraft，
 * 因此可以脱离游戏直接跑联网冒烟测试。HTTP 细节（重试、限速、下载校验）复用 {@link Http}。
 */
public class ModrinthClient {

    private final ModrinthSettings settings;

    public ModrinthClient(ModrinthSettings settings) {
        this.settings = settings;
    }

    public ModrinthSettings settings() {
        return settings;
    }

    // ------------------------------------------------------------ 查询接口

    /** 搜索项目。 */
    public SearchResult search(SearchQuery query, boolean filterLoader) throws ModrinthException {
        List<List<String>> facets = query.facets(filterLoader);
        StringBuilder url = new StringBuilder("/search?limit=").append(query.limit)
                .append("&offset=").append(query.offset)
                .append("&index=").append(encode(query.index));
        if (query.query != null && !query.query.isBlank()) {
            url.append("&query=").append(encode(query.query));
        }
        if (!facets.isEmpty()) {
            url.append("&facets=").append(encode(toJson(facets)));
        }

        JsonObject root = getJson(url.toString());
        SearchResult result = new SearchResult();
        result.offset = intOr(root, "offset", query.offset);
        result.limit = intOr(root, "limit", query.limit);
        result.totalHits = intOr(root, "total_hits", 0);
        for (JsonElement hit : arrayOrEmpty(root, "hits")) {
            result.hits.add(parseHit(hit.getAsJsonObject()));
        }
        return result;
    }

    /** 取某个项目的全部版本（可按游戏版本 / 加载器过滤）。 */
    public List<ProjectVersion> listVersions(String projectIdOrSlug, ContentType type,
                                            String gameVersion, String loader) throws ModrinthException {
        StringBuilder url = new StringBuilder("/project/").append(encode(projectIdOrSlug)).append("/version");
        List<String> params = new ArrayList<>();
        if (gameVersion != null && !gameVersion.isBlank()) {
            params.add("game_versions=" + encode("[\"" + gameVersion + "\"]"));
        }
        if (loader != null && !loader.isBlank() && type != null && type.loaderSpecific()) {
            params.add("loaders=" + encode("[\"" + loader + "\"]"));
        }
        if (!params.isEmpty()) {
            url.append('?').append(String.join("&", params));
        }
        JsonArray array = getJsonArray(url.toString());
        List<ProjectVersion> versions = new ArrayList<>();
        for (JsonElement element : array) {
            versions.add(parseVersion(element.getAsJsonObject()));
        }
        return versions;
    }

    /** 单个版本详情。 */
    public ProjectVersion version(String versionId) throws ModrinthException {
        return parseVersion(getJson("/version/" + encode(versionId)));
    }

    /** 项目详情。 */
    public ProjectSummary project(String idOrSlug) throws ModrinthException {
        return parseProject(getJson("/project/" + encode(idOrSlug)));
    }

    /** 汇总版的连通性检查，用于设置界面里的“测试连接”。 */
    public boolean ping() {
        try {
            getJson("/search?limit=1");
            return true;
        } catch (ModrinthException e) {
            Log.warn("[PackHub] Modrinth 连通性检查失败: %s", e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------ 下载

    /**
     * 下载一个版本文件到目标路径：先写 .part，校验 sha1 后原子替换。
     */
    public Path download(VersionFile file, Path target, ProgressListener listener) throws ModrinthException {
        if (file == null || file.url == null || file.url.isBlank()) {
            throw new ModrinthException("该版本没有可下载文件", 0);
        }
        try {
            return Http.download(resolveUrl(file.url), target, listener, file.size, file.sha1, httpOptions());
        } catch (SourceException e) {
            throw new ModrinthException(e.getMessage(), e.statusCode(), e);
        }
    }

    /** 把 CDN 地址按用户配置改写（例如换镜像）。 */
    public String resolveUrl(String url) {
        if (url == null) {
            return "";
        }
        String override = settings.cdnBaseOverride;
        if (override != null && !override.isBlank()
                && url.startsWith("https://cdn.modrinth.com/")) {
            String base = override.endsWith("/") ? override.substring(0, override.length() - 1) : override;
            return base + url.substring("https://cdn.modrinth.com".length());
        }
        return url;
    }

    // ------------------------------------------------------------ HTTP 底层

    private JsonObject getJson(String path) throws ModrinthException {
        String body = request(path);
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject()) {
                throw new ModrinthException("响应不是 JSON 对象: " + Http.preview(body), 0);
            }
            return element.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new ModrinthException("解析 JSON 失败: " + Http.preview(body), 0, e);
        }
    }

    private JsonArray getJsonArray(String path) throws ModrinthException {
        String body = request(path);
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonArray()) {
                throw new ModrinthException("响应不是 JSON 数组: " + Http.preview(body), 0);
            }
            return element.getAsJsonArray();
        } catch (RuntimeException e) {
            throw new ModrinthException("解析 JSON 失败: " + Http.preview(body), 0, e);
        }
    }

    private String request(String path) throws ModrinthException {
        List<String> bases = new ArrayList<>();
        bases.add(settings.apiBase);
        boolean fallbackUsable = settings.useFallbackWhenPrimaryFails
                && settings.apiBaseFallback != null
                && !settings.apiBaseFallback.isBlank()
                && !settings.apiBaseFallback.equals(settings.apiBase);
        if (fallbackUsable) {
            bases.add(settings.apiBaseFallback);
        }

        ModrinthException lastError = null;
        for (String base : bases) {
            String url = trimSlash(base) + path;
            try {
                return Http.get(url, httpOptions());
            } catch (SourceException e) {
                lastError = new ModrinthException(e.getMessage(), e.statusCode(), e);
                if (bases.size() > 1) {
                    Log.warn("[PackHub] API 地址 %s 失败（%s），尝试备用地址", base, e.getMessage());
                }
            }
        }
        throw lastError != null ? lastError : new ModrinthException("请求失败: " + path, 0);
    }

    private Http.Options httpOptions() {
        return new Http.Options()
                .connectTimeout(settings.connectTimeoutSeconds)
                .readTimeout(settings.readTimeoutSeconds)
                .retries(settings.downloadRetries)
                .userAgent(settings.userAgent);
    }

    // ------------------------------------------------------------ JSON 解析

    private ProjectSummary parseHit(JsonObject json) {
        ProjectSummary summary = new ProjectSummary();
        summary.source = ContentSource.MODRINTH;
        summary.projectId = stringOr(json, "project_id", "");
        summary.slug = stringOr(json, "slug", "");
        summary.title = stringOr(json, "title", "");
        summary.description = stringOr(json, "description", "");
        summary.author = stringOr(json, "author", "");
        summary.iconUrl = stringOr(json, "icon_url", "");
        summary.downloads = longOr(json, "downloads", 0);
        summary.follows = longOr(json, "follows", 0);
        summary.dateModified = stringOr(json, "date_modified", "");
        summary.latestVersion = stringOr(json, "latest_version", "");
        summary.license = stringOr(json, "license", "");
        summary.clientSide = stringOr(json, "client_side", "unknown");
        summary.serverSide = stringOr(json, "server_side", "unknown");
        summary.type = ContentType.byModrinthType(stringOr(json, "project_type", "mod"));
        fill(summary.categories, json, "categories");
        fill(summary.versions, json, "versions");
        fill(summary.loaders, json, "loaders");
        return summary;
    }

    private ProjectSummary parseProject(JsonObject json) {
        ProjectSummary summary = new ProjectSummary();
        summary.source = ContentSource.MODRINTH;
        summary.projectId = stringOr(json, "id", "");
        summary.slug = stringOr(json, "slug", "");
        summary.title = stringOr(json, "title", "");
        summary.description = stringOr(json, "description", "");
        summary.downloads = longOr(json, "downloads", 0);
        summary.follows = longOr(json, "followers", 0);
        summary.iconUrl = stringOr(json, "icon_url", "");
        summary.clientSide = stringOr(json, "client_side", "unknown");
        summary.serverSide = stringOr(json, "server_side", "unknown");
        summary.type = ContentType.byModrinthType(stringOr(json, "project_type", "mod"));
        summary.dateModified = stringOr(json, "updated", "");
        fill(summary.versions, json, "game_versions");
        fill(summary.categories, json, "categories");
        fill(summary.loaders, json, "loaders");
        JsonElement license = json.get("license");
        if (license != null && license.isJsonObject()) {
            summary.license = stringOr(license.getAsJsonObject(), "id", "");
        }
        return summary;
    }

    private ProjectVersion parseVersion(JsonObject json) {
        ProjectVersion version = new ProjectVersion();
        version.source = ContentSource.MODRINTH;
        version.id = stringOr(json, "id", "");
        version.projectId = stringOr(json, "project_id", "");
        version.name = stringOr(json, "name", "");
        version.versionNumber = stringOr(json, "version_number", "");
        version.versionType = stringOr(json, "version_type", "release");
        version.datePublished = stringOr(json, "date_published", "");
        version.downloads = longOr(json, "downloads", 0);
        fill(version.gameVersions, json, "game_versions");
        fill(version.loaders, json, "loaders");
        for (JsonElement element : arrayOrEmpty(json, "files")) {
            JsonObject fileJson = element.getAsJsonObject();
            VersionFile file = new VersionFile();
            file.url = stringOr(fileJson, "url", "");
            file.filename = stringOr(fileJson, "filename", "");
            file.primary = boolOr(fileJson, "primary", false);
            file.size = longOr(fileJson, "size", 0);
            JsonObject hashes = fileJson.has("hashes") && fileJson.get("hashes").isJsonObject()
                    ? fileJson.getAsJsonObject("hashes") : new JsonObject();
            file.sha1 = stringOr(hashes, "sha1", "");
            file.sha512 = stringOr(hashes, "sha512", "");
            version.files.add(file);
        }
        for (JsonElement element : arrayOrEmpty(json, "dependencies")) {
            JsonObject depJson = element.getAsJsonObject();
            VersionDependency dependency = new VersionDependency();
            dependency.versionId = stringOr(depJson, "version_id", "");
            dependency.projectId = stringOr(depJson, "project_id", "");
            dependency.fileName = stringOr(depJson, "file_name", "");
            dependency.dependencyType = stringOr(depJson, "dependency_type", "required");
            version.dependencies.add(dependency);
        }
        return version;
    }

    // ------------------------------------------------------------ 小工具

    private static void fill(List<String> target, JsonObject json, String key) {
        for (JsonElement element : arrayOrEmpty(json, key)) {
            if (element.isJsonPrimitive()) {
                target.add(element.getAsString());
            }
        }
    }

    private static JsonArray arrayOrEmpty(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int intOr(JsonObject json, String key, int fallback) {
        JsonElement element = json.get(key);
        try {
            return element == null || element.isJsonNull() ? fallback : element.getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static long longOr(JsonObject json, String key, long fallback) {
        JsonElement element = json.get(key);
        try {
            return element == null || element.isJsonNull() ? fallback : element.getAsLong();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean boolOr(JsonObject json, String key, boolean fallback) {
        JsonElement element = json.get(key);
        try {
            return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String toJson(List<List<String>> facets) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < facets.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('[');
            List<String> group = facets.get(i);
            for (int j = 0; j < group.size(); j++) {
                if (j > 0) {
                    sb.append(',');
                }
                sb.append('"').append(group.get(j).replace("\"", "\\\"")).append('"');
            }
            sb.append(']');
        }
        return sb.append(']').toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
