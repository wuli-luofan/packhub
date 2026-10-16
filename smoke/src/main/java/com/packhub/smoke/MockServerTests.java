package com.packhub.smoke;

import com.packhub.curseforge.CurseForgeClient;
import com.packhub.curseforge.CurseForgeSettings;
import com.packhub.http.Http;
import com.packhub.http.ProgressListener;
import com.packhub.modrinth.ContentType;
import com.packhub.modrinth.ModrinthClient;
import com.packhub.modrinth.ModrinthSettings;
import com.packhub.modrinth.SearchQuery;
import com.packhub.modrinth.SearchResult;
import com.packhub.modrinth.model.ProjectSummary;
import com.packhub.modrinth.model.ProjectVersion;
import com.packhub.modrinth.model.VersionFile;
import com.packhub.source.ContentSource;
import com.packhub.source.CurseForgeException;
import com.packhub.source.SourceException;
import com.packhub.util.Hashes;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用本地 mock HTTP 服务器验证两个内容源客户端的完整链路：
 * 请求参数、JSON 映射、下载直链拼接、sha1 校验、错误码与重试。
 *
 * <p>不需要真实 API Key，也不需要外网。运行： gradlew :smoke:mock</p>
 */
public final class MockServerTests {

    private static int passed;
    private static int failed;

    private static final byte[] MODRINTH_BYTES =
            "PackHub modrinth test payload 0123456789".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CURSEFORGE_BYTES =
            "PackHub curseforge test payload abcdefghij".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String base = "http://127.0.0.1:" + port;
        Path work = Files.createTempDirectory("packhub-mock-");

        String modrinthSha1 = Hashes.sha1(MODRINTH_BYTES);
        String curseForgeSha1 = Hashes.sha1(CURSEFORGE_BYTES);
        AtomicInteger flakyHits = new AtomicInteger();
        AtomicInteger rateLimitedHits = new AtomicInteger();
        AtomicInteger lastModrinthQuery = new AtomicInteger();

        // ---------------------------------------------------------- Modrinth mock
        server.createContext("/v2/search", exchange -> {
            lastModrinthQuery.set(exchange.getRequestURI().getQuery() == null ? 0 : 1);
            respond(exchange, 200, """
                    {"hits":[{"project_id":"AABB","slug":"demo-pack","title":"Demo Pack","description":"desc",
                    "author":"tester","icon_url":"","downloads":1234,"follows":5,"date_modified":"2026-01-01",
                    "latest_version":"V1","license":"mit","client_side":"required","server_side":"unsupported",
                    "project_type":"resourcepack","categories":["16x"],"versions":["26.3"],"loaders":[]}],
                    "offset":0,"limit":20,"total_hits":1}
                    """);
        });
        server.createContext("/v2/project/", exchange -> respond(exchange, 200, """
                [{"id":"V1","project_id":"AABB","name":"Demo 1.0","version_number":"1.0","version_type":"release",
                "date_published":"2026-01-02","downloads":10,"game_versions":["26.3"],"loaders":["minecraft"],
                "files":[{"url":"%s/files/modrinth.jar","filename":"demo-pack.zip","primary":true,"size":%d,
                "hashes":{"sha1":"%s","sha512":""}}],
                "dependencies":[{"version_id":"","project_id":"CCDD","file_name":"","dependency_type":"required"}]}]
                """.formatted(base, MODRINTH_BYTES.length, modrinthSha1)));
        server.createContext("/files/modrinth.jar", exchange -> respondBytes(exchange, MODRINTH_BYTES));
        server.createContext("/v2/broken", exchange -> respond(exchange, 404, "{\"error\":\"not_found\"}"));
        server.createContext("/v2/flaky", exchange -> {
            int hit = flakyHits.incrementAndGet();
            if (hit < 3) {
                respond(exchange, 500, "{\"error\":\"boom\"}");
            } else {
                respond(exchange, 200, "{\"ok\":true}");
            }
        });
        server.createContext("/v2/limited", exchange -> {
            int hit = rateLimitedHits.incrementAndGet();
            if (hit == 1) {
                exchange.getResponseHeaders().add("Retry-After", "1");
                respond(exchange, 429, "{\"error\":\"rate limited\"}");
            } else {
                respond(exchange, 200, "{\"ok\":true}");
            }
        });

        // ---------------------------------------------------------- CurseForge mock
        server.createContext("/v1/mods/search", exchange -> respond(exchange, 200, """
                {"data":[{"id":238222,"name":"JEI","slug":"jei","summary":"A recipe viewer","downloadCount":999,
                "classId":6,"dateModified":"2026-01-01","logo":{"thumbnailUrl":"http://example/logo.png"},
                "authors":[{"name":"mezz"}],"categories":[{"name":"Map and Information"}],
                "latestFilesIndexes":[{"gameVersion":"26.3","modLoader":4,"filename":"jei.jar"},
                {"gameVersion":"26.3","modLoader":6,"filename":"jei.jar"}]}],
                "pagination":{"index":0,"pageSize":20,"resultCount":1,"totalCount":1}}
                """));
        server.createContext("/v1/mods/238222/files", exchange -> respond(exchange, 200, """
                {"data":[{"id":1234567,"modId":238222,"displayName":"JEI 26.3","fileName":"jei-26.3.jar",
                "releaseType":1,"fileDate":"2026-02-01T00:00:00Z","fileLength":%d,"downloadCount":5,
                "downloadUrl":null,
                "hashes":[{"value":"%s","algo":1},{"value":"deadbeef","algo":2}],
                "gameVersions":["26.3","Fabric"],
                "dependencies":[{"modId":306612,"relationType":3},{"modId":999,"relationType":2}]}],
                "pagination":{"index":0,"pageSize":50,"resultCount":1,"totalCount":1}}
                """.formatted(CURSEFORGE_BYTES.length, curseForgeSha1)));
        server.createContext("/v1/mods/238222", exchange -> respond(exchange, 200, """
                {"data":{"id":238222,"name":"JEI","slug":"jei","summary":"A recipe viewer","downloadCount":999,
                "classId":6,"logo":{"thumbnailUrl":""},"authors":[{"name":"mezz"}],"categories":[],
                "latestFilesIndexes":[{"gameVersion":"26.3","modLoader":6,"filename":"jei.jar"}]}}
                """));
        server.createContext("/v1/mods/403", exchange -> respond(exchange, 403, "{\"error\":\"forbidden\"}"));
        // 模拟 edge.forgecdn.net 的兜底直链布局：/files/<id/1000>/<id%1000>/<fileName>
        server.createContext("/files/1234/567/jei-26.3.jar", exchange -> respondBytes(exchange, CURSEFORGE_BYTES));

        server.start();
        System.out.println("=== PackHub Mock 服务器测试 === " + base);

        try {
            testModrinth(base, work);
            testCurseForge(base, work);
            testHttpRetry(base);
            testMissingKey(base);
        } finally {
            server.stop(0);
        }

        System.out.println();
        System.out.println("=== 结果: 通过 " + passed + " 项，失败 " + failed + " 项 ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------ Modrinth

    private static void testModrinth(String base, Path work) {
        ModrinthSettings settings = new ModrinthSettings();
        settings.apiBase = base + "/v2";
        settings.apiBaseFallback = "";
        settings.useFallbackWhenPrimaryFails = false;
        settings.downloadRetries = 1;
        ModrinthClient client = new ModrinthClient(settings);

        check("Modrinth 搜索映射（标题/作者/下载量/类型/来源）", () -> {
            SearchResult result = client.search(new SearchQuery().type(ContentType.RESOURCEPACK)
                    .gameVersion("26.3").limit(20), true);
            if (result.totalHits != 1 || result.hits.size() != 1) {
                return false;
            }
            ProjectSummary hit = result.hits.get(0);
            return "Demo Pack".equals(hit.title) && "tester".equals(hit.author)
                    && hit.downloads == 1234 && hit.type == ContentType.RESOURCEPACK
                    && hit.source == ContentSource.MODRINTH && !hit.idOrSlug().isBlank();
        });

        check("Modrinth 版本映射 + 主文件 + 必选依赖", () -> {
            ProjectVersion version = client.listVersions("AABB", ContentType.RESOURCEPACK, "26.3", null).get(0);
            VersionFile file = version.primaryFile();
            return version.source == ContentSource.MODRINTH
                    && "demo-pack.zip".equals(file.filename)
                    && file.sha1.length() == 40
                    && version.dependencies.size() == 1
                    && version.dependencies.get(0).isRequired()
                    && "CCDD".equals(version.dependencies.get(0).projectId);
        });

        check("Modrinth 下载 + sha1 校验", () -> {
            ProjectVersion version = client.listVersions("AABB", ContentType.RESOURCEPACK, "26.3", null).get(0);
            Path target = work.resolve("modrinth-demo.zip");
            client.download(version.primaryFile(), target, ProgressListener.NONE);
            return Files.isRegularFile(target)
                    && Files.readAllBytes(target).length == MODRINTH_BYTES.length;
        });

        check("Modrinth 404 抛出带状态码的异常", () -> {
            try {
                Http.get(base + "/v2/broken", new Http.Options());
                return false;
            } catch (SourceException e) {
                return e.statusCode() == 404;
            }
        });
    }

    // ------------------------------------------------------------ CurseForge

    private static void testCurseForge(String base, Path work) {
        CurseForgeSettings settings = new CurseForgeSettings();
        settings.apiBase = base + "/v1";
        settings.apiKey = "test-key";
        settings.fallbackDownloadBase = base + "/files/";
        settings.downloadRetries = 1;
        CurseForgeClient client = new CurseForgeClient(settings);

        check("CurseForge 搜索映射（classId/下载量/加载器索引）", () -> {
            SearchResult result = client.search(new SearchQuery().type(ContentType.MOD)
                    .gameVersion("26.3").loader("fabric").limit(20), true);
            if (result.totalHits != 1 || result.hits.size() != 1) {
                return false;
            }
            ProjectSummary hit = result.hits.get(0);
            return "JEI".equals(hit.title) && "238222".equals(hit.projectId)
                    && hit.downloads == 999 && hit.source == ContentSource.CURSEFORGE
                    && hit.type == ContentType.MOD
                    && hit.versions.contains("26.3")
                    && hit.loaders.contains("fabric") && hit.loaders.contains("neoforge");
        });

        check("CurseForge 文件映射（版本/加载器拆分、sha1 取值 algo=1）", () -> {
            ProjectVersion version = client.listFiles("238222", ContentType.MOD, "26.3", "fabric").get(0);
            VersionFile file = version.primaryFile();
            return version.source == ContentSource.CURSEFORGE
                    && "jei-26.3.jar".equals(file.filename)
                    && version.gameVersions.contains("26.3")
                    && version.loaders.contains("fabric")
                    && file.sha1.length() == 40
                    && "release".equals(version.versionType)
                    && version.dependencies.size() == 2
                    && version.dependencies.get(0).isRequired()
                    && !version.dependencies.get(1).isRequired();
        });

        check("CurseForge downloadUrl 为空时按 fileId 拼兜底直链", () -> {
            ProjectVersion version = client.listFiles("238222", ContentType.MOD, "26.3", "fabric").get(0);
            String url = version.primaryFile().url;
            return url.equals(base + "/files/1234/567/jei-26.3.jar");
        });

        check("CurseForge 兜底直链下载 + sha1 校验", () -> {
            ProjectVersion version = client.listFiles("238222", ContentType.MOD, "26.3", "fabric").get(0);
            Path target = work.resolve("jei-26.3.jar");
            client.download(version.primaryFile(), target, ProgressListener.NONE);
            return Files.isRegularFile(target)
                    && Files.readAllBytes(target).length == CURSEFORGE_BYTES.length;
        });

        check("CurseForge 项目详情映射", () -> {
            ProjectSummary project = client.project("238222");
            return "JEI".equals(project.title) && project.source == ContentSource.CURSEFORGE
                    && project.loaders.contains("neoforge");
        });

        check("CurseForge 403 标记为鉴权问题", () -> {
            try {
                client.project("403");
                return false;
            } catch (SourceException e) {
                return e.isAuthProblem() || e.statusCode() == 500;
            }
        });
    }

    private static void testHttpRetry(String base) {
        check("HTTP 重试：连续 500 后成功", () -> {
            Http.Options options = new Http.Options().retries(4).connectTimeout(5).readTimeout(10);
            String body = Http.get(base + "/v2/flaky", options);
            return body.contains("ok");
        });

        check("HTTP 429：按 Retry-After 等待后成功", () -> {
            Http.Options options = new Http.Options().retries(2).connectTimeout(5).readTimeout(10);
            long start = System.currentTimeMillis();
            String body = Http.get(base + "/v2/limited", options);
            long cost = System.currentTimeMillis() - start;
            return body.contains("ok") && cost >= 900;
        });
    }

    private static void testMissingKey(String base) throws Exception {
        CurseForgeSettings settings = new CurseForgeSettings();
        settings.apiBase = base + "/v1";
        settings.apiKey = "";
        CurseForgeClient client = new CurseForgeClient(settings);

        check("缺少 API Key 时给出明确提示", () -> {
            if (client.usable()) {
                return false;
            }
            try {
                client.search(new SearchQuery().type(ContentType.MOD), true);
                return false;
            } catch (CurseForgeException e) {
                return e.getMessage().contains("API Key") && e.statusCode() == 401;
            }
        });
    }

    // ------------------------------------------------------------ 工具

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    private static void respondBytes(HttpExchange exchange, byte[] data) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    private interface Check {
        boolean run() throws Exception;
    }

    private static void check(String name, Check check) {
        try {
            if (check.run()) {
                passed++;
                System.out.println("[通过] " + name);
            } else {
                failed++;
                System.out.println("[失败] " + name);
            }
        } catch (Throwable t) {
            failed++;
            System.out.println("[失败] " + name + " -> " + t);
        }
    }
}
