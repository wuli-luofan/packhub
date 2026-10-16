package com.packhub.curseforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.packhub.http.Http;
import com.packhub.http.ProgressListener;
import com.packhub.modrinth.ContentType;
import com.packhub.modrinth.SearchQuery;
import com.packhub.modrinth.SearchResult;
import com.packhub.modrinth.model.ProjectSummary;
import com.packhub.modrinth.model.ProjectVersion;
import com.packhub.modrinth.model.VersionDependency;
import com.packhub.modrinth.model.VersionFile;
import com.packhub.source.ContentSource;
import com.packhub.source.CurseForgeException;
import com.packhub.source.SourceException;
import com.packhub.util.Log;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CurseForge v1 API 客户端（纯 java.net + Gson，不依赖 Minecraft）。
 *
 * <p>解析结果统一映射成 Modrinth 形状的 {@link ProjectSummary} / {@link ProjectVersion} /
 * {@link VersionFile}，这样下载器与界面完全不用关心来源差异。</p>
 *
 * <p>需要用户自己的 API Key：{@code x-api-key} 头。没有 Key 时所有方法都会抛
 * {@link CurseForgeException#missingKey()}。</p>
 */
public class CurseForgeClient {

    /** CurseForge 的 classId。 */
    private static final int CLASS_MOD = 6;
    private static final int CLASS_RESOURCEPACK = 12;
    private static final int CLASS_SHADER = 6552;
    private static final int CLASS_DATAPACK = 6945;
    private static final int CLASS_MODPACK = 4471;

    private final CurseForgeSettings settings;

    public CurseForgeClient(CurseForgeSettings settings) {
        this.settings = settings;
    }

    public CurseForgeSettings settings() {
        return settings;
    }

    public boolean usable() {
        return settings.hasApiKey();
    }

    // ------------------------------------------------------------ 查询

    /** 搜索项目。 */
    public SearchResult search(SearchQuery query, boolean filterLoader) throws SourceException {
        requireKey();
        SearchResult result = searchOnce(query, filterLoader, classId(query.type));
        if (result.totalHits == 0 && query.type != ContentType.MOD) {
            // 某些内容类型在 CF 上的 classId 归类不一致，去掉 classId 再试一次
            SearchResult fallback = searchOnce(query, filterLoader, 0);
            if (fallback.totalHits > 0) {
                return fallback;
            }
        }
        return result;
    }

    private SearchResult searchOnce(SearchQuery query, boolean filterLoader, int classId) throws SourceException {
        StringBuilder url = new StringBuilder("/mods/search?gameId=").append(settings.minecraftGameId)
                .append("&index=").append(Math.max(0, query.offset))
                .append("&pageSize=").append(Math.min(50, Math.max(1, query.limit)));
        if (query.query != null && !query.query.isBlank()) {
            url.append("&searchFilter=").append(encode(query.query));
        }
        if (classId > 0) {
            url.append("&classId=").append(classId);
        }
        if (query.gameVersion != null && !query.gameVersion.isBlank()) {
            url.append("&gameVersion=").append(encode(query.gameVersion));
        }
        int loaderType = modLoaderType(filterLoader ? query.loader : null);
        if (loaderType > 0) {
            url.append("&modLoaderType=").append(loaderType);
        }
        int sortField = sortField(query.index);
        if (sortField > 0) {
            url.append("&sortField=").append(sortField).append("&sortOrder=desc");
        }

        JsonObject root = getJson(url.toString());
        SearchResult result = new SearchResult();
        JsonObject pagination = objectOrEmpty(root, "pagination");
        result.offset = intOr(pagination, "index", query.offset);
        result.limit = intOr(pagination, "pageSize", query.limit);
        result.totalHits = intOr(pagination, "totalCount", 0);
        for (JsonElement element : arrayOrEmpty(root, "data")) {
            if (element.isJsonObject()) {
                result.hits.add(parseMod(element.getAsJsonObject()));
            }
        }
        return result;
    }

    /** 某个项目的全部文件（可按游戏版本 / 加载器过滤）。 */
    public List<ProjectVersion> listFiles(String modId, ContentType type, String gameVersion, String loader)
            throws SourceException {
        requireKey();
        StringBuilder url = new StringBuilder("/mods/").append(encode(modId))
                .append("/files?index=0&pageSize=50");
        if (gameVersion != null && !gameVersion.isBlank()) {
            url.append("&gameVersion=").append(encode(gameVersion));
        }
        int loaderType = modLoaderType(type != null && type.loaderSpecific() ? loader : null);
        if (loaderType > 0) {
            url.append("&modLoaderType=").append(loaderType);
        }
        JsonObject root = getJson(url.toString());
        List<ProjectVersion> versions = new ArrayList<>();
        for (JsonElement element : arrayOrEmpty(root, "data")) {
            if (element.isJsonObject()) {
                versions.add(parseFile(element.getAsJsonObject(), modId, type));
            }
        }
        return versions;
    }

    /** 单个项目详情。 */
    public ProjectSummary project(String modId) throws SourceException {
        requireKey();
        JsonObject root = getJson("/mods/" + encode(modId));
        JsonElement data = root.get("data");
        if (data == null || !data.isJsonObject()) {
            throw new CurseForgeException("CurseForge 未返回项目信息: " + modId, 0);
        }
        return parseMod(data.getAsJsonObject());
    }

    /** 连通性 / Key 有效性检查。 */
    public boolean ping() {
        if (!settings.hasApiKey()) {
            return false;
        }
        try {
            getJson("/mods/search?gameId=" + settings.minecraftGameId + "&pageSize=1");
            return true;
        } catch (SourceException e) {
            Log.warn("[PackHub] CurseForge 连通性检查失败: %s", e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------ 下载

    public Path download(VersionFile file, Path target, ProgressListener listener) throws SourceException {
        if (file == null || file.url == null || file.url.isBlank()) {
            throw new CurseForgeException("该文件没有可用的下载地址（该项目可能禁止 API 分发）", 0);
        }
        return Http.download(resolveUrl(file.url), target, listener, file.size, file.sha1, httpOptions());
    }

    /**
     * 拼出文件的直链。
     * <p>CurseForge 的 {@code downloadUrl} 经常为 null，此时按官方 CDN 规则拼接：
     * {@code https://edge.forgecdn.net/files/<fileId/1000>/<fileId%1000>/<fileName>}。</p>
     */
    public String fallbackUrl(long fileId, String fileName) {
        String base = settings.fallbackDownloadBase;
        if (base == null || base.isBlank()) {
            base = "https://edge.forgecdn.net/files/";
        }
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return base + (fileId / 1000) + "/" + (fileId % 1000) + "/" + fileName;
    }

    /** 允许把 CDN 换成镜像。 */
    public String resolveUrl(String url) {
        if (url == null) {
            return "";
        }
        String override = settings.cdnBaseOverride;
        if (override != null && !override.isBlank()) {
            String base = override.endsWith("/") ? override.substring(0, override.length() - 1) : override;
            for (String host : new String[]{"https://edge.forgecdn.net", "https://mediafilez.forgecdn.net",
                    "https://media.forgecdn.net"}) {
                if (url.startsWith(host)) {
                    return base + url.substring(host.length());
                }
            }
        }
        return url;
    }

    // ------------------------------------------------------------ 映射

    private ProjectSummary parseMod(JsonObject json) {
        ProjectSummary summary = new ProjectSummary();
        summary.source = ContentSource.CURSEFORGE;
        summary.projectId = String.valueOf(longOr(json, "id", 0));
        summary.slug = stringOr(json, "slug", "");
        summary.title = stringOr(json, "name", "");
        summary.description = stringOr(json, "summary", "");
        summary.downloads = longOr(json, "downloadCount", 0);
        summary.dateModified = stringOr(json, "dateModified", "");
        summary.type = typeByClassId(intOr(json, "classId", CLASS_MOD));
        JsonObject logo = objectOrEmpty(json, "logo");
        summary.iconUrl = stringOr(logo, "thumbnailUrl", stringOr(logo, "url", ""));
        JsonArray authors = arrayOrEmpty(json, "authors");
        if (authors.size() > 0 && authors.get(0).isJsonObject()) {
            summary.author = stringOr(authors.get(0).getAsJsonObject(), "name", "");
        }
        for (JsonElement element : arrayOrEmpty(json, "categories")) {
            if (element.isJsonObject()) {
                String name = stringOr(element.getAsJsonObject(), "name", "");
                if (!name.isBlank()) {
                    summary.categories.add(name);
                }
            }
        }
        for (JsonElement element : arrayOrEmpty(json, "latestFilesIndexes")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject index = element.getAsJsonObject();
            String gameVersion = stringOr(index, "gameVersion", "");
            if (!gameVersion.isBlank() && !summary.versions.contains(gameVersion)) {
                summary.versions.add(gameVersion);
            }
            String loader = loaderName(intOr(index, "modLoader", 0));
            if (loader != null && !summary.loaders.contains(loader)) {
                summary.loaders.add(loader);
            }
        }
        summary.latestVersion = summary.versions.isEmpty() ? "" : summary.versions.get(0);
        return summary;
    }

    private ProjectVersion parseFile(JsonObject json, String modId, ContentType type) {
        ProjectVersion version = new ProjectVersion();
        version.source = ContentSource.CURSEFORGE;
        long fileId = longOr(json, "id", 0);
        version.id = String.valueOf(fileId);
        version.projectId = modId;
        version.name = stringOr(json, "displayName", stringOr(json, "fileName", ""));
        version.versionNumber = version.name;
        version.versionType = releaseType(intOr(json, "releaseType", 1));
        version.datePublished = stringOr(json, "fileDate", "");
        version.downloads = longOr(json, "downloadCount", 0);

        String fileName = stringOr(json, "fileName", "");
        for (JsonElement element : arrayOrEmpty(json, "gameVersions")) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String value = element.getAsString();
            String loader = loaderName(value);
            if (loader != null) {
                if (!version.loaders.contains(loader)) {
                    version.loaders.add(loader);
                }
            } else if (!value.isBlank() && !version.gameVersions.contains(value)) {
                version.gameVersions.add(value);
            }
        }

        VersionFile file = new VersionFile();
        file.filename = fileName;
        file.size = longOr(json, "fileLength", 0);
        file.primary = true;
        file.sha1 = sha1Of(json);
        JsonElement downloadUrl = json.get("downloadUrl");
        if (downloadUrl != null && !downloadUrl.isJsonNull()) {
            file.url = downloadUrl.getAsString();
        }
        if (file.url == null || file.url.isBlank()) {
            file.url = fallbackUrl(fileId, fileName);
        }
        version.files.add(file);

        for (JsonElement element : arrayOrEmpty(json, "dependencies")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject dependencyJson = element.getAsJsonObject();
            VersionDependency dependency = new VersionDependency();
            dependency.projectId = String.valueOf(longOr(dependencyJson, "modId", 0));
            dependency.versionId = "";
            dependency.fileName = "";
            dependency.dependencyType = relationType(intOr(dependencyJson, "relationType", 3));
            version.dependencies.add(dependency);
        }
        return version;
    }

    private static String sha1Of(JsonObject fileJson) {
        for (JsonElement element : arrayOrEmpty(fileJson, "hashes")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject hash = element.getAsJsonObject();
            if (intOr(hash, "algo", 0) == 1) {
                return stringOr(hash, "value", "");
            }
        }
        return "";
    }

    private JsonObject getJson(String path) throws SourceException {
        String url = trimSlash(settings.apiBase) + path;
        String body = Http.get(url, httpOptions());
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject()) {
                throw new CurseForgeException("响应不是 JSON 对象: " + Http.preview(body), 0);
            }
            return element.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new CurseForgeException("解析 JSON 失败: " + Http.preview(body), 0, e);
        }
    }

    private Http.Options httpOptions() {
        return new Http.Options()
                .connectTimeout(settings.connectTimeoutSeconds)
                .readTimeout(settings.readTimeoutSeconds)
                .retries(settings.downloadRetries)
                .userAgent(settings.userAgent)
                .header("x-api-key", settings.apiKey);
    }

    private void requireKey() throws CurseForgeException {
        if (!settings.hasApiKey()) {
            throw CurseForgeException.missingKey();
        }
    }

    // ------------------------------------------------------------ 枚举映射

    private static int classId(ContentType type) {
        return switch (type) {
            case MOD -> CLASS_MOD;
            case RESOURCEPACK -> CLASS_RESOURCEPACK;
            case SHADER -> CLASS_SHADER;
            case DATAPACK -> CLASS_DATAPACK;
            case MODPACK -> CLASS_MODPACK;
        };
    }

    private static ContentType typeByClassId(int classId) {
        return switch (classId) {
            case CLASS_RESOURCEPACK -> ContentType.RESOURCEPACK;
            case CLASS_SHADER -> ContentType.SHADER;
            case CLASS_DATAPACK -> ContentType.DATAPACK;
            case CLASS_MODPACK -> ContentType.MODPACK;
            default -> ContentType.MOD;
        };
    }

    /** modLoaderType: 1 Forge / 4 Fabric / 5 Quilt / 6 NeoForge。 */
    private static int modLoaderType(String loader) {
        if (loader == null || loader.isBlank()) {
            return 0;
        }
        return switch (loader.toLowerCase(Locale.ROOT)) {
            case "forge" -> 1;
            case "fabric" -> 4;
            case "quilt" -> 5;
            case "neoforge" -> 6;
            default -> 0;
        };
    }

    /** 把 file.gameVersions 里的加载器名标准化。 */
    private static String loaderName(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "forge" -> "forge";
            case "neoforge" -> "neoforge";
            case "fabric" -> "fabric";
            case "quilt" -> "quilt";
            case "liteloader" -> "liteloader";
            case "rift" -> "rift";
            case "cauldron" -> "cauldron";
            default -> null;
        };
    }

    private static String loaderName(int modLoaderType) {
        return switch (modLoaderType) {
            case 1 -> "forge";
            case 4 -> "fabric";
            case 5 -> "quilt";
            case 6 -> "neoforge";
            default -> null;
        };
    }

    /** 1 release / 2 beta / 3 alpha。 */
    private static String releaseType(int value) {
        return switch (value) {
            case 2 -> "beta";
            case 3 -> "alpha";
            default -> "release";
        };
    }

    /** 1 Embedded / 2 Optional / 3 Required / 4 Tool / 5 Incompatible / 6 Include。 */
    private static String relationType(int value) {
        return switch (value) {
            case 1 -> "embedded";
            case 2 -> "optional";
            case 4 -> "tool";
            case 5 -> "incompatible";
            case 6 -> "include";
            default -> "required";
        };
    }

    /** Modrinth 的排序名 -> CurseForge sortField（1 Featured / 2 Popularity / 3 LastUpdated / 6 TotalDownloads / 11 ReleasedDate）。 */
    private static int sortField(String index) {
        if (index == null) {
            return 0;
        }
        return switch (index.toLowerCase(Locale.ROOT)) {
            case "downloads" -> 6;
            case "follows" -> 2;
            case "newest" -> 11;
            case "updated" -> 3;
            default -> 0;
        };
    }

    // ------------------------------------------------------------ 小工具

    private static JsonArray arrayOrEmpty(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static JsonObject objectOrEmpty(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
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
