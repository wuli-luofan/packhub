package com.packhub.smoke;

import com.packhub.modrinth.ContentType;
import com.packhub.modrinth.ModrinthClient;
import com.packhub.modrinth.ModrinthException;
import com.packhub.modrinth.ModrinthSettings;
import com.packhub.modrinth.SearchQuery;
import com.packhub.modrinth.SearchResult;
import com.packhub.modrinth.model.ProjectSummary;
import com.packhub.modrinth.model.ProjectVersion;
import com.packhub.modrinth.model.VersionFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 联网冒烟测试：验证 Modrinth 搜索 / 版本查询 / 文件下载 / sha1 校验这条完整链路。
 * 只依赖 common 里不接 Minecraft 的那部分代码。
 *
 * <p>运行： gradlew :smoke:run --args=""</p>
 */
public final class SmokeTest {

    private static final String GAME_VERSION = "26.3";
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        Path work = Path.of(args.length > 0 ? args[0] : "build/smoke-output");
        ModrinthSettings settings = new ModrinthSettings();
        ModrinthClient client = new ModrinthClient(settings);

        System.out.println("=== PackHub Modrinth 冒烟测试（Minecraft " + GAME_VERSION + "）===");
        System.out.println("API: " + settings.apiBase);

        check("连通性 (/search)", client::ping);

        SearchResult shaders = search(client, ContentType.SHADER, "shader", null);
        check("搜索光影包", () -> shaders.totalHits > 0);
        printHits("光影", shaders);

        SearchResult packs = search(client, ContentType.RESOURCEPACK, "resourcepack", null);
        check("搜索资源包", () -> packs.totalHits > 0);
        printHits("资源包", packs);

        SearchResult mods = search(client, ContentType.MOD, "mod", "fabric");
        check("搜索 Fabric 模组", () -> mods.totalHits > 0);
        printHits("Fabric 模组", mods);

        SearchResult neoMods = search(client, ContentType.MOD, "mod", "neoforge");
        check("搜索 NeoForge 模组", () -> neoMods.totalHits > 0);
        printHits("NeoForge 模组", neoMods);

        // 版本查询 + 真实下载：挑一个体积小的资源包
        ProjectSummary target = firstWithSlug(packs, "bare-bones");
        if (target == null && !packs.hits.isEmpty()) {
            target = packs.hits.get(0);
        }
        if (target != null) {
            ProjectSummary subject = target;
            check("查询版本列表 (" + subject.slug + ")", () -> {
                List<ProjectVersion> versions = client.listVersions(subject.idOrSlug(), ContentType.RESOURCEPACK,
                        GAME_VERSION, null);
                System.out.println("    " + subject.title + " 有 " + versions.size() + " 个适配 " + GAME_VERSION + " 的版本");
                return !versions.isEmpty();
            });

            final ProjectSummary chosen = subject;
            check("下载并校验 sha1 (" + chosen.slug + ")", () -> {
                List<ProjectVersion> versions = client.listVersions(chosen.idOrSlug(), ContentType.RESOURCEPACK,
                        GAME_VERSION, null);
                if (versions.isEmpty()) {
                    return false;
                }
                VersionFile file = smallestFile(versions);
                if (file == null) {
                    return false;
                }
                Path destination = work.resolve(file.filename);
                client.download(file, destination, (done, total) ->
                        System.out.println("    进度 " + done + "/" + total));
                boolean exists = Files.isRegularFile(destination);
                System.out.println("    已保存 " + destination.toAbsolutePath() + " (" + Files.size(destination) + " 字节)");
                return exists && !file.sha1.isBlank();
            });
        }

        System.out.println();
        System.out.println("=== 结果: 通过 " + passed + " 项，失败 " + failed + " 项 ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static VersionFile smallestFile(List<ProjectVersion> versions) {
        VersionFile best = null;
        for (ProjectVersion version : versions) {
            VersionFile file = version.primaryFile();
            if (file == null) {
                continue;
            }
            if (best == null || (file.size > 0 && file.size < best.size)) {
                best = file;
            }
        }
        return best;
    }

    private static ProjectSummary firstWithSlug(SearchResult result, String slug) {
        for (ProjectSummary hit : result.hits) {
            if (slug.equalsIgnoreCase(hit.slug)) {
                return hit;
            }
        }
        return null;
    }

    private static SearchResult search(ModrinthClient client, ContentType type, String label, String loader) {
        try {
            SearchQuery query = new SearchQuery()
                    .query(type == ContentType.MOD ? "sodium" : "")
                    .type(type)
                    .gameVersion(GAME_VERSION)
                    .loader(loader)
                    .limit(5)
                    .index("downloads");
            return client.search(query, loader != null);
        } catch (ModrinthException e) {
            System.out.println("  ! " + label + " 搜索异常: " + e.getMessage());
            SearchResult empty = new SearchResult();
            return empty;
        }
    }

    private static void printHits(String label, SearchResult result) {
        List<String> lines = new ArrayList<>();
        for (ProjectSummary hit : result.hits) {
            lines.add("    - " + hit.title + " (" + hit.slug + ") " + hit.prettyDownloads()
                    + " 下载 · 版本 " + String.join("/", hit.versions.subList(0, Math.min(3, hit.versions.size()))));
        }
        System.out.println("  " + label + " 命中 " + result.totalHits + " 个，前几个：");
        lines.forEach(System.out::println);
    }

    private interface Check {
        boolean run() throws Exception;
    }

    private static void check(String name, Check check) {
        try {
            boolean ok = check.run();
            if (ok) {
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
