# PackHub 验证记录

本文件记录本次交付**实际跑过**的验证，以及**因环境限制没跑成**的部分。所有数字都来自真实执行输出。

## 0. 环境与版本矩阵

| 项目 | 值 | 来源 |
| --- | --- | --- |
| 目标游戏版本 | Minecraft **26.3**（2026-09-15 发布，Java 25） | [Modrinth game_version API](https://api.modrinth.com/v2/tag/game_version) + [Mojang version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) |
| 编译用 JDK | OpenJDK **25.0.1** LTS（`java-runtime-epsilon`，随游戏分发的 JDK） | `java -version` |
| Gradle | **9.8.1**（wrapper 已内置，默认下载源为腾讯云镜像） | `gradlew --version` |
| Fabric Loader | **0.19.5**（stable） | `meta.fabricmc.net /v2/versions/loader/26.3` |
| Fabric API | **0.163.0+26.3** | `maven.fabricmc.net` fabric-api maven-metadata.xml |
| Fabric Loom | **1.19.1**（release） | `maven.fabricmc.net` fabric-loom maven-metadata.xml |
| Yarn 映射 | **26.3 无 Yarn**（返回 `[]`）→ 工程改用 Mojang 官方映射 | `meta.fabricmc.net /v2/versions/yarn/26.3` |
| NeoForge | **26.3.0.77**（release） | neoforge maven-metadata.xml（经 BMCLAPI 镜像获取） |
| CurseForge API | `https://api.curseforge.com/v1`，`x-api-key` 鉴权，Minecraft `gameId=432` | [CurseForge 官方文档](https://docs.curseforge.com/rest-api/) |

## 0.5 最终结果：两个 jar 已由 CI 真实构建成功

本机网络拿不到 Minecraft 工具链（第 5 节），所以构建放在 GitHub Actions 里做。

**运行记录**：<https://github.com/wuli-luofan/packhub/actions/runs/38840490248> —— `conclusion: success`

| 产物 | 大小 | 校验内容 |
| --- | --- | --- |
| `packhub-fabric-1.0.2.jar` | 184,403 字节 | 88 个条目；`fabric.mod.json`（`id=packhub`、`version=1.0.2`、入口 `com.packhub.fabric.PackHubFabric`、`minecraft ~26.3`、`java >=25`、`environment=client`）；`com/packhub/**`；`assets/packhub/icon.png`；中英 `lang` |
| `packhub-neoforge-1.0.2.jar` | 182,643 字节 | 88 个条目；`META-INF/neoforge.mods.toml`（`modId=packhub`、入口 `com.packhub.neoforge.PackHubNeoForge`、`neoforge >=26.3`、`minecraft [26.3,26.4)`）；`com/packhub/**`；图标；中英 `lang` |
| `packhub-fabric-1.0.2-sources.jar` | 89,900 字节 | 源码 jar |
| `packhub-neoforge-1.0.2-sources.jar` | 90,000 字节 | 源码 jar |

除此之外，该次运行里每个步骤都是 success：API 签名探测、**离线逻辑测试 16/16**、
**Mock 服务器测试 13/13**、**构建 Fabric jar**、构建 NeoForge jar、真实 Modrinth 冒烟测试。

> 顺带一提：元数据里的中文描述已按 UTF-8 校验通过（`fabric.mod.json` 内
> `"description": "游戏内下载资源包 / 材质包 / 光影包 / 模组，并支持热加载…"`），
> 且两个模块的 `processResources` 都显式设置了 `filteringCharset = 'UTF-8'`，
> 避免在默认编码非 UTF-8 的机器上写坏中文。

## 0.6 为通过 26.3 编译所做的 API 迁移

26.1 起 Minecraft 不再混淆，26.3 又换掉了 GLFW 与整套 GUI 渲染类。完整的差异表在
[`minecraft-26.3-api-notes.md`](minecraft-26.3-api-notes.md)，要点：

- **删掉 `mappings`**：26.3 的版本 JSON 里没有 `client_mappings`，留 `loom.officialMojangMappings()`
  会直接报 `Failed to find official mojang mappings for 26.3`；依赖也从 `modImplementation` 换成 `implementation`。
- `GuiGraphics` → **`GuiGraphicsExtractor`**；`Screen#render(...)` → **`extractRenderState(...)`**；
  `drawString(...)` → **`text(...)`**；文本颜色必须 ARGB；`Minecraft#setScreen` → **`minecraft.gui.setScreen`**。
- `mouseClicked(double,double,int)` → **`mouseClicked(MouseButtonEvent, boolean)`**；
  `keyPressed(int,int,int)` → **`keyPressed(KeyEvent)`**。
- `net.minecraft.Util` **已被删除**（打开目录/链接改为反射多候选 + `Desktop` + 系统命令三层兜底）。
- `InputConstants.Type.KEYSYM` 不存在了 → `KeyMapping` 的构造改成**全反射**（按构造函数形状匹配 + 动态挑枚举常量）。
- Fabric API：`KeyBindingHelper` → **`KeyMappingHelper`**（包 `...client.keymapping.v1`）；
  `ClientCommandManager` → **`ClientCommands`**。

这些改动不是一次性猜出来的，而是**靠 CI 的编译错误 + 一个专门打印真实签名的探测步骤**逐轮收敛的
（共 3 轮：24 个错误 → 0 个错误）。

## 1. 离线逻辑测试：**16/16 通过**

命令：`gradlew :smoke:logic`（等价的直接运行方式见第 6 节）

覆盖：

```
[通过] 识别 Fabric 模组元数据            (fabric.mod.json)
[通过] 识别 NeoForge 模组元数据          (META-INF/neoforge.mods.toml)
[通过] 识别 PackHub 插件                (packhub-plugin.json)
[通过] 无法识别时回退到文件名
[通过] jar 类型判定 (ModJarInspector)
[通过] 扫描 mods/ 与 mods-disabled/（忽略非 jar 文件）
[通过] 已安装模组 id 列表（小写）
[通过] 扫描 mods/ 下的 jar 文件
[通过] 待重启队列：去重 / 移除 / 持久化
[通过] 待重启队列：目录不存在时自动创建
[通过] 搜索分面：模组带游戏版本与加载器过滤
[通过] 搜索分面：资源包不受加载器影响
[通过] 搜索分面：关闭加载器过滤后不含 categories
[通过] 版本兼容判断与主文件选择
[通过] sha1 计算正确                    (abc → a9993e…d89d)
[通过] sha1 文件校验
=== 结果: 通过 16 项，失败 0 项 ===
```

## 2. Mock 服务器测试：**13/13 通过**

命令：`gradlew :smoke:mock`。本地起 `com.sun.net.httpserver.HttpServer`，
把两个客户端指到 `http://127.0.0.1:<随机端口>`，**不需要外网、不需要 API Key**。

```
=== PackHub Mock 服务器测试 === http://127.0.0.1:11176
[通过] Modrinth 搜索映射（标题/作者/下载量/类型/来源）
[通过] Modrinth 版本映射 + 主文件 + 必选依赖
[通过] Modrinth 下载 + sha1 校验
[通过] Modrinth 404 抛出带状态码的异常
[通过] CurseForge 搜索映射（classId/下载量/加载器索引）
[通过] CurseForge 文件映射（版本/加载器拆分、sha1 取值 algo=1）
[通过] CurseForge downloadUrl 为空时按 fileId 拼兜底直链
[通过] CurseForge 兜底直链下载 + sha1 校验
[通过] CurseForge 项目详情映射
[通过] CurseForge 403 标记为鉴权问题
[通过] HTTP 重试：连续 500 后成功
[通过] HTTP 429：按 Retry-After 等待后成功
[通过] 缺少 API Key 时给出明确提示
=== 结果: 通过 13 项，失败 0 项 ===
```

其中 CurseForge 兜底直链这条最关键：mock 里故意把 `downloadUrl` 设为 `null`，
断言客户端拼出 `http://127.0.0.1:<port>/files/1234/567/jei-26.3.jar`
（即官方 CDN 规则 `edge.forgecdn.net/files/<fileId/1000>/<fileId%1000>/<fileName>`），
并且真的从这个地址下载成功、sha1 通过。

## 3. 真实 Modrinth 联网冒烟测试：**7/7 通过**（网络可用时）

命令：`gradlew :smoke:run`

```
=== PackHub Modrinth 冒烟测试（Minecraft 26.3）===
[通过] 连通性 (/search)
[通过] 搜索光影包          命中 241 个
[通过] 搜索资源包          命中 4875 个
[通过] 搜索 Fabric 模组    命中 27 个（query=sodium + versions:26.3 + categories:fabric）
[通过] 搜索 NeoForge 模组  命中 15 个（同上，categories:neoforge）
[通过] 查询版本列表 (fresh-animations)   → 1 个适配 26.3 的版本
[通过] 下载并校验 sha1 (fresh-animations)
       已保存 FreshAnimations_v1.10.5.zip（645,816 字节）
=== 结果: 通过 7 项，失败 0 项 ===
```

> 第二轮复测时本机网络恶化到连 `api.modrinth.com` 都连接超时，因此这一项依赖网络状况；
> 第 1、2 节的两组测试完全不依赖外网，随时可复现。

## 4. 全量源码编译（内部一致性检查）：**0 错误**

本机拿不到 Minecraft 工具链（见第 5 节），为了确认**工程自身没有编码错误**，
我为用到的 Minecraft / Fabric / NeoForge API 写了一套桩类（`tools/api-stubs`），
用 **JDK 25** 编译 `common + fabric + neoforge` 的全部源码：

```
Compiling 104 source files (JDK 25 + API stubs)...
Diagnostics: 0  (expected 0)
PASS: 135 class files produced.
```

能证明：没有漏 import、没有调用不存在的自有方法/字段、泛型与重载解析正确、
两个加载器模块与 common 之间没有冲突，CurseForge 接入后仍保持一致。

**不能**证明：桩类签名与真实 Minecraft 26.3 / Fabric API / NeoForge 完全一致。
真实 API 的最终校验必须在能联网下载依赖的机器上跑 `gradlew build`。

## 5. 未能完成：本机 `gradlew build`（网络链路限制）

Gradle 在解析 Fabric Loom 插件依赖时卡住。逐项诊断结果：

| 探测 | 结果 |
| --- | --- |
| `GET maven.fabricmc.net/.../fabric-loom-1.19.1.jar` | `contentLength=1570019`，跑到 **111,176 字节**后停滞，读超时 |
| 分段请求 `Range: bytes=0-65535` | 读超时（服务器未按 Range 返回） |
| `GET fabric-loader-0.19.5.jar`（约 700 KB） | 读超时 |
| `GET fabric-api-0.163.0+26.3.jar` | 读超时 |
| 用 Node.js（OpenSSL 栈）重试同一地址 | 同样卡死 → **链路层限制，与 TLS 客户端无关** |
| BMCLAPI 镜像 `net/fabricmc/fabric-loom/...` | HTTP 404（镜像未收录） |
| BMCLAPI 镜像 `net/fabricmc/fabric-api/...` | HTTP 404（镜像未收录） |
| 腾讯云 / 阿里云 / Maven Central / 华为云 的 fabric-loom | 404 |
| GitHub Release 里的 fabric-loom jar | 404（上游没有发布） |
| `maven.nova-committee.cn`（另一个曾用的 fabric 镜像） | 连接被拒绝（站点已下线） |
| 小文件（metadata / pom / 47 KB 级） | 正常 200 → 说明不是整体断网，而是**大响应被掐断** |
| 第二轮：`api.modrinth.com/v2/search` | 也变成 Connect timed out（网络进一步恶化） |
| **第三轮**：`fabric-loom-1.19.1.jar` | 仍然读超时（连续三轮同样的失败） |
| **第三轮**：`maven.neoforged.net` 的 neoforge universal jar | Connect timed out |
| **第三轮**：`api.modrinth.com/v2/tag/loader` | 恢复，200（25,622 字节） |
| **第三轮**：`api.curseforge.com/v1/mods/search` | **可达**：无 Key 返回 403（说明只差一个 API Key，不是网络问题） |

结论：`fabric-loom`、`fabric-api`、`fabric-loader` 的大 jar 在这台机器上无法下载，
NeoForge 侧同样需要 `maven.neoforged.net` 的大文件（BMCLAPI 的 neoforge universal jar 也超时），
所以两个加载器的**真实 jar 无法在本机产出**。这是环境限制，不是工程配置问题。
（同样的失败在连续三轮里复现，证据见上表。）

替代路径（已随交付提供）：`.github/workflows/build.yml` 在 GitHub 运行器上执行
`gradle build`，并把 `fabric/build/libs/*.jar`、`neoforge/build/libs/*.jar` 作为 artifact 上传。

## 6. 如何自己复现这些验证

```powershell
# ① 离线逻辑测试 + mock 服务器测试（不需要外网）
gradlew :smoke:logic
gradlew :smoke:mock

# ② 真实 API 冒烟测试（需要外网）
gradlew :smoke:run

# ③ 全量桩编译（0 错误）：桩类已随工程交付在 tools/api-stubs
powershell -ExecutionPolicy Bypass -File .\tools\verify-offline.ps1 `
    -Jdk25 "$env:APPDATA\.minecraft\runtime\java-runtime-epsilon"

# ④ 大文件传输诊断（判断是不是网络把大响应掐断了）
java .\tools\network\RangeProbe.java https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.1/fabric-loom-1.19.1.jar
```

> 本机因为 Loom 拉不下来，`gradlew` 的所有任务都跑不起来（Gradle 配置阶段就会卡住），
> 所以第 1、2、3 节的测试在本机是直接用 `javac` + `java` 跑同名 main 类完成的，
> 结果与 Gradle 任务完全一致。

## 7. 拿到 jar 后建议的验证顺序

1. `gradlew build`（首次会下载 Minecraft 26.3、映射与依赖，约 1–2 GB）。
2. 若报错集中在少数 API 改名上，按报错微调；已知高风险点：
   `KeyMapping` 构造（已反射兜底）、`Options.resourcePacks`（已反射兜底）、
   `SystemToast.addOrUpdate`（已反射兜底）、`Minecraft.getSingleplayerServer()/getWorldPath`（已反射兜底）。
3. 装进 `.minecraft/mods/`（Fabric 端还需 Fabric API），进游戏按 **P** 或输入 `/packhub`。
4. Modrinth 侧：搜索一个资源包 → 下载 → 应立即重载生效；下载一个光影 → 应在 Iris 列表出现；
   下载一个普通模组 → 应进入「热加载」页的待生效队列，点「一键软重启并载入」后新进程带上它。
5. CurseForge 侧：设置里填 Key → 点「测试 CurseForge」→ 搜索页把「源」切到 CurseForge →
   搜索 `JEI` → 版本页应能看到文件列表（含兜底直链），安装后走同样的热加载流程。
6. 插件热加载：把 `examples/example-plugin` 编译出的 jar 丢进 `config/packhub/plugins/`，
   在「已安装 → 热加载插件」里点「重载」，界面应显示 `tick=…` 状态行并在卸载时打印回调日志。
