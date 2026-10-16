package com.packhub.smoke;

import com.packhub.hotload.JarKind;
import com.packhub.hotload.ModJarInspector;
import com.packhub.hotload.PendingRestart;
import com.packhub.installed.InstalledModIndex;
import com.packhub.modrinth.ContentType;
import com.packhub.modrinth.SearchQuery;
import com.packhub.modrinth.model.ProjectVersion;
import com.packhub.modrinth.model.VersionFile;
import com.packhub.platform.Platform;
import com.packhub.util.Hashes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 不依赖 Minecraft 的逻辑测试：jar 元数据识别、已安装扫描、待重启队列持久化、
 * 搜索分面构造、版本兼容判断、sha1。
 *
 * <p>运行： gradlew :smoke:run  或直接 java -cp ... com.packhub.smoke.LogicTests</p>
 */
public final class LogicTests {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("packhub-logic-");
        System.out.println("=== PackHub 离线逻辑测试 === 工作目录: " + work);

        // ---------------------------------------------------------- 准备测试用 jar
        Path mods = work.resolve("mods");
        Path disabled = work.resolve("mods-disabled");
        Files.createDirectories(mods);
        Files.createDirectories(disabled);

        Path fabricJar = work.resolve("sodium-fabric.jar");
        writeJar(fabricJar, "fabric.mod.json",
                "{\n  \"schemaVersion\": 1,\n  \"id\": \"sodium\",\n  \"name\": \"Sodium\",\n  \"version\": \"0.6.0\"\n}\n");

        Path neoJar = work.resolve("example-neoforge.jar");
        writeJar(neoJar, "META-INF/neoforge.mods.toml",
                "modLoader=\"javafml\"\n[[mods]]\nmodId=\"examplemod\"\ndisplayName=\"Example Mod\"\nversion=\"1.2.3\"\n");

        Path pluginJar = work.resolve("example-plugin.jar");
        writeJar(pluginJar, "packhub-plugin.json",
                "{\n  \"id\": \"example-plugin\",\n  \"name\": \"Example Hot-Load Plugin\",\n  \"version\": \"1.0.0\",\n  \"main\": \"com.example.ExamplePlugin\",\n  \"apiVersion\": 1\n}\n");

        Path plainJar = work.resolve("mystery-1.0.jar");
        writeJar(plainJar, "some/package/Thing.class", new byte[]{0, 1, 2, 3});

        Files.copy(fabricJar, mods.resolve("sodium-fabric.jar"));
        Files.copy(plainJar, mods.resolve("mystery-1.0.jar"));
        Files.copy(neoJar, disabled.resolve("example-neoforge.jar"));
        Files.write(mods.resolve("not-a-mod.txt"), "hello".getBytes(StandardCharsets.UTF_8));

        // ---------------------------------------------------------- jar 元数据识别
        check("识别 Fabric 模组元数据", () -> {
            InstalledModIndex.Entry entry = InstalledModIndex.read(fabricJar);
            return "sodium".equals(entry.modId) && "Sodium".equals(entry.name)
                    && "0.6.0".equals(entry.version) && "fabric".equals(entry.loader) && !entry.plugin;
        });
        check("识别 NeoForge 模组元数据", () -> {
            InstalledModIndex.Entry entry = InstalledModIndex.read(neoJar);
            return "examplemod".equals(entry.modId) && "Example Mod".equals(entry.name)
                    && "1.2.3".equals(entry.version) && "neoforge".equals(entry.loader) && !entry.plugin;
        });
        check("识别 PackHub 插件", () -> {
            InstalledModIndex.Entry entry = InstalledModIndex.read(pluginJar);
            return "example-plugin".equals(entry.modId) && entry.plugin
                    && "packhub-plugin".equals(entry.loader);
        });
        check("无法识别时回退到文件名", () -> {
            InstalledModIndex.Entry entry = InstalledModIndex.read(plainJar);
            return "mystery-1.0".equals(entry.modId) && "unknown".equals(entry.loader);
        });

        check("jar 类型判定 (ModJarInspector)", () ->
                ModJarInspector.inspect(fabricJar) == JarKind.FABRIC_MOD
                        && ModJarInspector.inspect(neoJar) == JarKind.NEOFORGE_MOD
                        && ModJarInspector.inspect(pluginJar) == JarKind.PACKHUB_PLUGIN
                        && ModJarInspector.inspect(plainJar) == JarKind.UNKNOWN);

        // ---------------------------------------------------------- 已安装扫描
        Platform fake = new FakePlatform(work, mods);
        check("扫描 mods/ 与 mods-disabled/（忽略非 jar 文件）", () -> {
            List<InstalledModIndex.Entry> entries = InstalledModIndex.scan(fake);
            if (entries.size() != 3) {
                System.out.println("    实际条目: " + entries.size());
                return false;
            }
            boolean hasDisabled = entries.stream().anyMatch(e ->
                    e.path.getParent().getFileName().toString().equals("mods-disabled"));
            return hasDisabled;
        });
        check("已安装模组 id 列表（小写）", () -> {
            List<String> ids = InstalledModIndex.installedModIds(fake);
            return ids.contains("sodium") && ids.contains("examplemod");
        });
        check("扫描 mods/ 下的 jar 文件", () ->
                InstalledModIndex.installedJars(fake).size() == 2);

        // ---------------------------------------------------------- 待重启队列
        Path pendingFile = work.resolve("pending-restart.json");
        check("待重启队列：去重 / 移除 / 持久化", () -> {
            PendingRestart first = new PendingRestart(pendingFile);
            first.add("a.jar");
            first.add("a.jar");
            first.add("  ");
            boolean dedup = first.size() == 1;
            first.add("b.jar");
            first.remove("a.jar");
            boolean removed = first.size() == 1 && first.list().contains("b.jar");
            PendingRestart reloaded = new PendingRestart(pendingFile);
            boolean persisted = reloaded.list().equals(List.of("b.jar"));
            reloaded.clear();
            return dedup && removed && persisted && new PendingRestart(pendingFile).isEmpty();
        });
        check("待重启队列：目录不存在时自动创建", () -> {
            Path nested = work.resolve("deep/nested/dir/pending.json");
            PendingRestart queue = new PendingRestart(nested);
            queue.add("x.jar");
            return Files.isRegularFile(nested) && queue.size() == 1;
        });

        // ---------------------------------------------------------- 搜索分面
        check("搜索分面：模组带游戏版本与加载器过滤", () -> {
            SearchQuery query = new SearchQuery().query("sodium").type(ContentType.MOD)
                    .gameVersion("26.3").loader("fabric").limit(20).offset(0).index("downloads");
            List<List<String>> facets = query.facets(true);
            return facets.equals(List.of(
                    List.of("project_type:mod"),
                    List.of("versions:26.3"),
                    List.of("categories:fabric")));
        });
        check("搜索分面：资源包不受加载器影响", () -> {
            SearchQuery query = new SearchQuery().type(ContentType.RESOURCEPACK)
                    .gameVersion("26.3").loader("fabric");
            List<List<String>> facets = query.facets(true);
            return facets.equals(List.of(
                    List.of("project_type:resourcepack"),
                    List.of("versions:26.3")));
        });
        check("搜索分面：关闭加载器过滤后不含 categories", () -> {
            SearchQuery query = new SearchQuery().type(ContentType.SHADER)
                    .gameVersion("26.3").loader("fabric");
            List<List<String>> facets = query.facets(false);
            boolean hasLoaderFacet = facets.stream().anyMatch(group -> group.stream()
                    .anyMatch(value -> value.startsWith("categories:")));
            return !hasLoaderFacet
                    && facets.contains(List.of("project_type:shader"))
                    && facets.contains(List.of("versions:26.3"));
        });

        // ---------------------------------------------------------- 版本判断
        check("版本兼容判断与主文件选择", () -> {
            ProjectVersion version = new ProjectVersion();
            version.gameVersions.add("26.3");
            version.loaders.add("fabric");
            VersionFile secondary = new VersionFile();
            secondary.filename = "extra.jar";
            VersionFile primary = new VersionFile();
            primary.filename = "main.jar";
            primary.primary = true;
            primary.size = 1536;
            version.files.add(secondary);
            version.files.add(primary);
            return version.supports("26.3", "fabric")
                    && !version.supports("26.3", "neoforge")
                    && !version.supports("1.21.1", "fabric")
                    && version.primaryFile() == primary
                    && primary.prettySize().contains("1.5");
        });

        // ---------------------------------------------------------- 摘要与哈希
        check("sha1 计算正确", () ->
                "a9993e364706816aba3e25717850c26c9cd0d89d"
                        .equals(Hashes.sha1("abc".getBytes(StandardCharsets.UTF_8))));
        check("sha1 文件校验", () -> {
            Path file = work.resolve("hash.txt");
            Files.writeString(file, "abc", StandardCharsets.UTF_8);
            return Hashes.sha1(file).equals("a9993e364706816aba3e25717850c26c9cd0d89d");
        });

        // ---------------------------------------------------------- 安装记录（联机同步的清单来源）
        check("安装记录：写入 / 去重 / 按文件保留最新", () -> {
            Path registryFile = work.resolve("install-registry.json");
            com.packhub.installed.InstallRegistry registry = new com.packhub.installed.InstallRegistry(registryFile);
            registry.record(resultFor("Demo Mod", "demo-mod.jar", "AABB", "V1", "1.0", ContentType.MOD, 1000));
            registry.record(resultFor("Demo Mod", "demo-mod.jar", "AABB", "V2", "1.1", ContentType.MOD, 2000));
            registry.record(resultFor("Demo Pack", "demo-pack.zip", "CCDD", "P1", "2.0", ContentType.RESOURCEPACK, 500));
            com.packhub.installed.InstallRegistry reloaded =
                    new com.packhub.installed.InstallRegistry(registryFile);
            return reloaded.size() == 2
                    && reloaded.byFileName("demo-mod.jar").versionNumber.equals("1.1")
                    && reloaded.byFileName("demo-mod.jar").size == 2000;
        });
        check("安装记录：只把还在 mods/ 里的模组算作可同步", () -> {
            Path registryFile = work.resolve("install-registry-2.json");
            Path modsDir = work.resolve("mods-for-sync");
            Files.createDirectories(modsDir);
            com.packhub.installed.InstallRegistry registry = new com.packhub.installed.InstallRegistry(registryFile);
            registry.record(resultFor("Present Mod", "present.jar", "1111", "V1", "1.0", ContentType.MOD, 100));
            registry.record(resultFor("Missing Mod", "missing.jar", "2222", "V1", "1.0", ContentType.MOD, 100));
            registry.record(resultFor("A Pack", "pack.zip", "3333", "V1", "1.0", ContentType.RESOURCEPACK, 100));
            Files.write(modsDir.resolve("present.jar"), new byte[]{1, 2, 3});
            List<com.packhub.installed.InstallRegistry.Entry> shareable = registry.shareableMods(modsDir);
            return shareable.size() == 1 && shareable.get(0).fileName.equals("present.jar");
        });

        System.out.println();
        System.out.println("=== 结果: 通过 " + passed + " 项，失败 " + failed + " 项 ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------ 工具

    private static void writeJar(Path target, String entryName, String content) throws IOException {
        writeJar(target, entryName, content.getBytes(StandardCharsets.UTF_8));
    }

    /** 造一个 InstallResult，用来测试安装记录。 */
    private static com.packhub.download.InstallResult resultFor(String title, String fileName, String projectId,
                                                               String versionId, String versionNumber,
                                                               ContentType type, long size) {
        com.packhub.modrinth.model.ProjectSummary project = new com.packhub.modrinth.model.ProjectSummary();
        project.title = title;
        project.slug = fileName.replace(".jar", "").replace(".zip", "");
        project.projectId = projectId;
        project.source = com.packhub.source.ContentSource.MODRINTH;

        com.packhub.modrinth.model.ProjectVersion version = new com.packhub.modrinth.model.ProjectVersion();
        version.id = versionId;
        version.versionNumber = versionNumber;

        com.packhub.modrinth.model.VersionFile file = new com.packhub.modrinth.model.VersionFile();
        file.filename = fileName;
        file.url = "https://example.invalid/" + fileName;
        file.sha1 = "0".repeat(40);
        file.size = size;

        return new com.packhub.download.InstallResult(project, version, file, type,
                Path.of("out").resolve(fileName), false, false, "测试");
    }

    private static void writeJar(Path target, String entryName, byte[] content) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content);
            zip.closeEntry();
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

    /** 极简 Platform 实现，只用目录。 */
    private static final class FakePlatform implements Platform {
        private final Path gameDir;
        private final Path modsDir;

        FakePlatform(Path gameDir, Path modsDir) {
            this.gameDir = gameDir;
            this.modsDir = modsDir;
        }

        @Override
        public String loaderName() {
            return "Fabric";
        }

        @Override
        public String loaderVersion() {
            return "test";
        }

        @Override
        public Path gameDir() {
            return gameDir;
        }

        @Override
        public Path configDir() {
            return gameDir.resolve("config");
        }

        @Override
        public Path modsDir() {
            return modsDir;
        }

        @Override
        public Set<String> loadedModIds() {
            return new LinkedHashSet<>(List.of("fabricloader", "packhub"));
        }
    }
}
