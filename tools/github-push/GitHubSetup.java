import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * PackHub GitHub 推送助手。
 *
 * <p>为什么要这个工具：本机 <code>https://github.com</code> 的 TLS 握手被网络中断，
 * 但 <code>api.github.com</code> 与 <code>github.com:22</code>（SSH）是通的。
 * 所以推代码要么走 SSH，要么直接走 GitHub API（本工具实现）。</p>
 *
 * <pre>
 * 用法（token 放环境变量 GITHUB_TOKEN，或用 --token 传入）：
 *   java GitHubSetup.java whoami                          查看当前 token 对应的账号
 *   java GitHubSetup.java repo packhub [--private]        创建仓库
 *   java GitHubSetup.java keygen [--name packhub]         生成 SSH 密钥（调 ssh-keygen）
 *   java GitHubSetup.java addkey [--title packhub] [--key-file 路径]   把公钥注册到账号
 *   java GitHubSetup.java upload &lt;owner&gt;/&lt;repo&gt; [--branch main] [--message "..."] [--dry-run]
 *                                                         用 Git Data API 直接上传整个工程
 * </pre>
 */
public class GitHubSetup {

    private static final String API = "https://api.github.com";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static String token = System.getenv("GITHUB_TOKEN");
    private static Path projectRoot;

    public static void main(String[] args) throws Exception {
        projectRoot = locateProjectRoot();
        List<String> rest = new ArrayList<>(Arrays.asList(args));
        boolean dryRun = rest.remove("--dry-run");
        boolean createPrivate = rest.remove("--private");
        String branch = takeOption(rest, "--branch", "main");
        String message = takeOption(rest, "--message", "PackHub: 由 GitHub API 上传");
        String title = takeOption(rest, "--title", "PackHub");
        String keyFile = takeOption(rest, "--key-file", null);
        String name = takeOption(rest, "--name", "packhub");
        String explicitToken = takeOption(rest, "--token", null);
        String projectOption = takeOption(rest, "--project", null);
        if (projectOption != null) {
            projectRoot = Paths.get(projectOption).toAbsolutePath();
        }
        if (explicitToken != null) {
            token = explicitToken;
        }

        if (rest.isEmpty()) {
            usage();
            return;
        }
        String command = rest.remove(0);
        switch (command) {
            case "whoami" -> whoami();
            case "repo" -> {
                if (rest.isEmpty()) {
                    System.out.println("请给出仓库名： repo <name> [--private]");
                    return;
                }
                createRepo(rest.get(0), createPrivate);
            }
            case "keygen" -> keygen(name, takeOption(rest, "--out", null));
            case "addkey" -> addKey(title, keyFile);
            case "upload" -> {
                if (rest.isEmpty()) {
                    System.out.println("请给出 owner/repo");
                    return;
                }
                upload(rest.get(0), branch, message, dryRun);
            }
            default -> usage();
        }
    }

    private static void usage() {
        System.out.println("""
                PackHub GitHub 推送助手（token 放在 GITHUB_TOKEN 环境变量里）

                  whoami                                   查看 token 对应账号
                  repo <name> [--private]                   创建仓库
                  keygen [--name packhub] [--out 目录]       生成 SSH 密钥（默认 ~/.ssh）
                  addkey [--title packhub] [--key-file F]   注册公钥到 GitHub 账号
                  upload <owner>/<repo> [--branch main] [--message "..."] [--dry-run]
                                                            用 API 上传整个工程（HTTPS 被墙时的兜底）

                可选：--project <packhub 目录>  指定工程根目录（默认从当前目录往上找 settings.gradle）
                """);
    }

    // ------------------------------------------------------------ 子命令

    private static void whoami() throws Exception {
        requireToken();
        Response response = request("GET", API + "/user", null);
        if (response.code != 200) {
            fail("token 无效或权限不足", response);
        }
        System.out.println("账号: " + field(response.body, "login"));
        System.out.println("名字: " + field(response.body, "name"));
        System.out.println("主页: " + field(response.body, "html_url"));
    }

    private static void createRepo(String name, boolean isPrivate) throws Exception {
        requireToken();
        String body = "{\"name\":\"" + escape(name) + "\",\"private\":" + isPrivate
                + ",\"auto_init\":false,\"description\":\"PackHub - Minecraft 26.3 dual-loader content downloader & hot-loader\"}";
        Response response = request("POST", API + "/user/repos", body);
        if (response.code == 201) {
            String owner = field(response.body, "full_name");
            System.out.println("仓库已创建: https://github.com/" + owner);
            System.out.println("下一步（SSH 推送）：");
            System.out.println("  git remote add origin git@github.com:" + owner + ".git");
            System.out.println("  git push -u origin main");
            return;
        }
        if (response.code == 422) {
            System.out.println("仓库已存在（422），直接推送即可。");
            return;
        }
        fail("创建仓库失败", response);
    }

    private static void keygen(String name, String outDir) throws Exception {
        Path sshDir = outDir != null ? Paths.get(outDir).toAbsolutePath()
                : Paths.get(System.getProperty("user.home"), ".ssh");
        Files.createDirectories(sshDir);
        Path keyPath = sshDir.resolve("id_ed25519_" + name);
        if (Files.exists(keyPath)) {
            System.out.println("已存在密钥: " + keyPath + "（跳过生成）");
        } else {
            String sshKeygen = findSshKeygen();
            if (sshKeygen == null) {
                System.out.println("找不到 ssh-keygen.exe，请手动生成密钥：");
                System.out.println("  ssh-keygen -t ed25519 -C \"你@邮箱\" -f " + keyPath);
                return;
            }
            ProcessBuilder builder = new ProcessBuilder(sshKeygen, "-t", "ed25519", "-C", "packhub",
                    "-f", keyPath.toString(), "-N", "");
            builder.inheritIO();
            Process process = builder.start();
            int code = process.waitFor();
            if (code != 0 && !Files.exists(keyPath)) {
                System.out.println("ssh-keygen 失败，退出码 " + code);
                return;
            }
        }
        Path pub = Paths.get(keyPath + ".pub");
        boolean repaired = false;
        if (!Files.exists(pub) || Files.size(pub) == 0) {
            // 某些受限/沙箱环境下 ssh-keygen 写不出 .pub，这里直接从私钥里解析出公钥
            String derived = derivePublicKey(keyPath);
            if (derived != null) {
                try {
                    Files.writeString(pub, derived + " packhub\n", StandardCharsets.UTF_8);
                    repaired = true;
                } catch (IOException e) {
                    // 写不进去也不影响：下面会把公钥直接打印出来，注册时也能从私钥解析
                    System.out.println("提示：写 " + pub.getFileName() + " 失败（" + e.getClass().getSimpleName()
                            + "），已改为直接打印公钥，注册公钥时会自动从私钥解析。");
                }
            }
        }
        System.out.println("私钥: " + keyPath);
        System.out.println("公钥: " + pub + (repaired ? "（由私钥解析生成）" : ""));
        String publicKey = null;
        if (Files.exists(pub) && Files.size(pub) > 0) {
            publicKey = Files.readString(pub).trim();
        } else {
            publicKey = derivePublicKey(keyPath);
        }
        if (publicKey != null) {
            System.out.println("--- 公钥内容（复制到 GitHub → Settings → SSH keys 也行）---");
            System.out.println(publicKey);
            System.out.println("------------------------------------------------------");
        } else {
            System.out.println("无法解析公钥，请手动执行: ssh-keygen -y -f \"" + keyPath + "\"");
        }
    }

    /**
     * 从 OpenSSH 私钥（未加密）里解析出公钥，避免依赖 ssh-keygen 写文件。
     * 容器格式：magic "openssh-key-v1\0" + string ciphername + string kdfname + string kdfoptions
     * + uint32 nkeys + string publickey[0] + string encrypted-private[0]。
     * 其中 publickey[0] 本身就是 SSH 线格式的 blob（string 算法名 + string 公钥内容）。
     */
    static String derivePublicKey(Path privateKey) {
        try {
            String text = Files.readString(privateKey, StandardCharsets.UTF_8);
            String base64 = text.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
            byte[] data = Base64.getDecoder().decode(base64);
            String magic = "openssh-key-v1\u0000";
            for (int i = 0; i < magic.length(); i++) {
                if (data[i] != (byte) magic.charAt(i)) {
                    return null;
                }
            }
            int offset = magic.length();
            offset = skipString(data, offset);   // ciphername
            offset = skipString(data, offset);   // kdfname
            offset = skipString(data, offset);   // kdfoptions
            offset += 4;                         // nkeys
            int blobLength = readUint32(data, offset);
            offset += 4;
            byte[] blob = Arrays.copyOfRange(data, offset, offset + blobLength);
            // blob 内部第一个 string 是算法名
            int algorithmLength = readUint32(blob, 0);
            String algorithm = new String(blob, 4, algorithmLength, StandardCharsets.UTF_8);
            return algorithm + " " + Base64.getEncoder().encodeToString(blob);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int skipString(byte[] data, int offset) {
        return offset + 4 + readUint32(data, offset);
    }

    private static int readUint32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static void addKey(String title, String keyFile) throws Exception {
        requireToken();
        Path pub;
        if (keyFile != null) {
            pub = Paths.get(keyFile);
        } else {
            Path sshDir = Paths.get(System.getProperty("user.home"), ".ssh");
            pub = sshDir.resolve("id_ed25519_packhub.pub");
            if (!Files.exists(pub)) {
                List<Path> candidates = new ArrayList<>();
                if (Files.isDirectory(sshDir)) {
                    try (var stream = Files.list(sshDir)) {
                        stream.filter(p -> p.getFileName().toString().endsWith(".pub")).forEach(candidates::add);
                    }
                }
                if (candidates.isEmpty()) {
                    System.out.println("没找到 .pub 公钥文件，请先跑 keygen，或用 --key-file 指定。");
                    return;
                }
                pub = candidates.get(0);
            }
        }
        String key = Files.readString(pub).trim();
        if (key.isEmpty()) {
            Path priv = Paths.get(pub.toString().replaceAll("\\.pub$", ""));
            String derived = derivePublicKey(priv);
            if (derived == null) {
                System.out.println("公钥文件是空的，且无法从私钥解析，请手动执行：ssh-keygen -y -f " + priv);
                return;
            }
            key = derived;
        }
        String body = "{\"title\":\"" + escape(title) + "\",\"key\":\"" + escape(key) + "\"}";
        Response response = request("POST", API + "/user/keys", body);
        if (response.code == 201) {
            System.out.println("公钥已注册到 GitHub 账号: " + pub.getFileName());
            return;
        }
        if (response.code == 422) {
            System.out.println("这把公钥已经注册过了（422）。");
            return;
        }
        fail("注册公钥失败", response);
    }

    /** 用 Git Data API 把整个工程上传成一个提交（不依赖 github.com 的 HTTPS）。 */
    private static void upload(String ownerRepo, String branch, String message, boolean dryRun) throws Exception {
        if (!dryRun) {
            requireToken();
        }
        List<Path> files = collectFiles();
        long totalBytes = files.stream().mapToLong(p -> {
            try {
                return Files.size(p);
            } catch (IOException e) {
                return 0;
            }
        }).sum();
        System.out.printf("待上传 %d 个文件，共 %.2f MB%n", files.size(), totalBytes / 1024.0 / 1024.0);
        boolean hasWorkflow = files.stream().anyMatch(p ->
                projectRoot.relativize(p).toString().replace('\\', '/').startsWith(".github/workflows/"));
        if (hasWorkflow) {
            System.out.println("提示：本次包含 .github/workflows/ 下的文件，token 需要额外具备 workflow 权限"
                    + "（classic token 勾选 repo + workflow），否则这一步会报 404/422。");
        }
        if (dryRun) {
            for (Path file : files) {
                System.out.println("  " + projectRoot.relativize(file).toString().replace('\\', '/'));
            }
            System.out.println("（--dry-run，未发起任何写请求）");
            return;
        }

        String[] parts = ownerRepo.split("/");
        if (parts.length != 2) {
            System.out.println("参数应为 owner/repo，例如 yourname/packhub");
            return;
        }
        String base = API + "/repos/" + parts[0] + "/" + parts[1];

        // 0) 全新空仓库不能直接用 Git Data API（会 409 Git Repository is empty），
        //    先用 Contents API 落一个初始提交，把默认分支建出来。
        String parentSha = refSha(base, branch);
        if (parentSha == null) {
            System.out.println("远端仓库还是空的，先用 Contents API 建初始提交…");
            initializeRepository(base, branch);
            parentSha = refSha(base, branch);
            if (parentSha == null) {
                System.out.println("初始化后仍读不到分支 " + branch + "，请确认仓库存在且 token 有 repo 权限。");
                return;
            }
            ensureDefaultBranch(base, branch);
        }

        // 1) 逐个文件建立 blob
        List<String[]> tree = new ArrayList<>();
        int index = 0;
        for (Path file : files) {
            String path = projectRoot.relativize(file).toString().replace('\\', '/');
            byte[] data = Files.readAllBytes(file);
            String content = Base64.getEncoder().encodeToString(data);
            String body = "{\"content\":\"" + content + "\",\"encoding\":\"base64\"}";
            Response response = request("POST", base + "/git/blobs", body);
            if (response.code != 201) {
                fail("创建 blob 失败: " + path, response);
            }
            String sha = field(response.body, "sha");
            String mode = path.equals("gradlew") ? "100755" : "100644";
            tree.add(new String[]{path, mode, sha});
            if (++index % 25 == 0) {
                System.out.println("  已上传 " + index + "/" + files.size());
            }
        }

        // 2) 建 tree
        StringBuilder treeJson = new StringBuilder("{\"tree\":[");
        for (int i = 0; i < tree.size(); i++) {
            String[] entry = tree.get(i);
            if (i > 0) {
                treeJson.append(',');
            }
            treeJson.append("{\"path\":\"").append(escape(entry[0]))
                    .append("\",\"mode\":\"").append(entry[1])
                    .append("\",\"type\":\"blob\",\"sha\":\"").append(entry[2]).append("\"}");
        }
        treeJson.append("]}");
        Response treeResponse = request("POST", base + "/git/trees", treeJson.toString());
        if (treeResponse.code != 201) {
            fail("创建 tree 失败", treeResponse);
        }
        String treeSha = field(treeResponse.body, "sha");

        // 3) 建 commit（parentSha 已在第 0 步拿到：要么是初始提交，要么是分支最新提交）
        String commitBody = "{\"message\":\"" + escape(message) + "\",\"tree\":\"" + treeSha + "\""
                + (parentSha == null ? "" : ",\"parents\":[\"" + parentSha + "\"]") + "}";
        Response commitResponse = request("POST", base + "/git/commits", commitBody);
        if (commitResponse.code != 201) {
            fail("创建 commit 失败", commitResponse);
        }
        String commitSha = field(commitResponse.body, "sha");

        // 4) 更新或创建分支引用
        if (parentSha == null) {
            String body = "{\"ref\":\"refs/heads/" + escape(branch) + "\",\"sha\":\"" + commitSha + "\"}";
            Response refResponse = request("POST", base + "/git/refs", body);
            if (refResponse.code != 201) {
                fail("创建分支失败", refResponse);
            }
        } else {
            String body = "{\"sha\":\"" + commitSha + "\",\"force\":false}";
            Response refResponse = request("PATCH", base + "/git/refs/heads/" + branch, body);
            if (refResponse.code != 200) {
                fail("更新分支失败", refResponse);
            }
        }
        System.out.println("上传完成: https://github.com/" + ownerRepo + "/tree/" + branch);
        System.out.println("commit: " + commitSha);
    }

    private static String refSha(String base, String branch) throws Exception {
        Response response = request("GET", base + "/git/ref/heads/" + branch, null);
        if (response.code == 200) {
            return field(response.body, "sha");
        }
        return null;
    }

    /** 空仓库初始化：用 Contents API 写一个 README，这会创建默认分支上的第一个提交。 */
    private static void initializeRepository(String base, String branch) throws Exception {
        String content = Base64.getEncoder().encodeToString("PackHub\n".getBytes(StandardCharsets.UTF_8));
        String body = "{\"message\":\"chore: 初始化仓库\",\"content\":\"" + content
                + "\",\"branch\":\"" + escape(branch) + "\"}";
        Response response = request("PUT", base + "/contents/README.md", body);
        if (response.code == 201 || response.code == 200) {
            System.out.println("初始提交已创建（README.md）。");
            return;
        }
        if (response.code == 422 || response.code == 409) {
            // 分支名与仓库默认分支不一致时重试一次，不带 branch
            String fallback = "{\"message\":\"chore: 初始化仓库\",\"content\":\"" + content + "\"}";
            Response retry = request("PUT", base + "/contents/README.md", fallback);
            if (retry.code == 201 || retry.code == 200) {
                System.out.println("初始提交已创建（默认分支）。");
                return;
            }
            fail("初始化空仓库失败", retry);
        }
        fail("初始化空仓库失败", response);
    }

    /** 保证仓库默认分支就是我们要推的分支（有些老账号默认是 master）。 */
    private static void ensureDefaultBranch(String base, String branch) throws Exception {
        Response info = request("GET", base, null);
        if (info.code == 200) {
            String defaultBranch = field(info.body, "default_branch");
            if (defaultBranch != null && !defaultBranch.isBlank() && !defaultBranch.equals(branch)) {
                String body = "{\"default_branch\":\"" + escape(branch) + "\"}";
                Response patch = request("PATCH", base, body);
                System.out.println(patch.code == 200
                        ? "已把默认分支从 " + defaultBranch + " 改成 " + branch
                        : "提示：默认分支仍是 " + defaultBranch + "（PATCH 返回 " + patch.code + "）");
            }
        }
    }

    // ------------------------------------------------------------ 基础设施

    private static List<Path> collectFiles() throws IOException {
        Set<String> skipDirs = Set.of(".git", "build", ".gradle", "run", "runs", ".idea", "out");
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(projectRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                Path relative = projectRoot.relativize(path);
                for (Path part : relative) {
                    if (skipDirs.contains(part.toString())) {
                        return;
                    }
                }
                files.add(path);
            });
        }
        files.sort(Comparator.comparing(p -> projectRoot.relativize(p).toString()));
        return files;
    }

    private static Path locateProjectRoot() {
        // 1) 通过 sun.java.command 拿到本脚本路径（单文件源码启动时它就是 .java 的路径）
        String command = System.getProperty("sun.java.command", "");
        String first = command.contains(" ") ? command.substring(0, command.indexOf(' ')) : command;
        if (first.endsWith(".java")) {
            Path script = Paths.get(first).toAbsolutePath();
            if (Files.isRegularFile(script) && script.getParent() != null
                    && script.getParent().getParent() != null && script.getParent().getParent().getParent() != null) {
                Path root = script.getParent().getParent().getParent();
                if (isProject(root)) {
                    return root;
                }
            }
        }
        // 2) 当前目录就是工程根
        Path current = Paths.get("").toAbsolutePath();
        if (isProject(current)) {
            return current;
        }
        // 3) 当前目录下有个叫 packhub 的子目录
        Path child = current.resolve("packhub");
        if (isProject(child)) {
            return child;
        }
        // 4) 从当前目录往上找
        Path parent = current;
        for (int i = 0; i < 8 && parent != null; i++) {
            if (isProject(parent)) {
                return parent;
            }
            parent = parent.getParent();
        }
        return current;
    }

    private static boolean isProject(Path path) {
        return path != null && Files.isRegularFile(path.resolve("settings.gradle"))
                && Files.isDirectory(path.resolve("common"));
    }

    private static String findSshKeygen() {
        String[] candidates = {
                "C:\\Windows\\System32\\OpenSSH\\ssh-keygen.exe",
                "C:\\Program Files\\Git\\usr\\bin\\ssh-keygen.exe"
        };
        for (String candidate : candidates) {
            if (Files.isRegularFile(Paths.get(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    private static void requireToken() {
        if (token == null || token.isBlank()) {
            System.out.println("缺少 token。请先申请一个 Personal Access Token（需要 repo 权限）：");
            System.out.println("  https://github.com/settings/tokens  （在能打开 GitHub 网页的设备/网络上操作）");
            System.out.println("然后在本机设置环境变量再运行，例如 PowerShell：");
            System.out.println("  $env:GITHUB_TOKEN = \"ghp_xxx\"");
            System.out.println("  java tools\\github-push\\GitHubSetup.java whoami");
            System.exit(2);
        }
    }

    private static final class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private static Response request(String method, String url, String body) throws Exception {
        // 用 java.net.http.HttpClient：HttpURLConnection 不支持 PATCH，而更新分支引用需要它
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .header("User-Agent", "PackHub-Setup/1.0")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (body != null) {
            builder.header("Content-Type", "application/json; charset=utf-8");
        }
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = CLIENT.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    private static void fail(String what, Response response) {
        System.out.println(what + "（HTTP " + response.code + "）：" + trim(response.body));
        System.exit(1);
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        String flat = value.replace('\n', ' ').trim();
        return flat.length() > 400 ? flat.substring(0, 400) + "..." : flat;
    }

    /** 取 JSON 里第一个 "key":"value"（够用即可，不引入依赖）。 */
    private static String field(String json, String key) {
        if (json == null) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String takeOption(List<String> args, String name, String fallback) {
        int index = args.indexOf(name);
        if (index >= 0 && index + 1 < args.size()) {
            String value = args.get(index + 1);
            args.remove(index + 1);
            args.remove(index);
            return value;
        }
        return fallback;
    }
}
