# PackHub · Minecraft 26.3 资源/光影/模组 一站式下载与热加载

PackHub 是一个**纯客户端**模组，同时提供 **Fabric** 与 **NeoForge** 两个版本，目标版本 **Minecraft 26.3**（Java 25）。
它在游戏里同时接上 **[Modrinth](https://modrinth.com/)** 与 **[CurseForge](https://www.curseforge.com/)** 两个内容库，
让你不用退出游戏就能搜索、下载并**立即生效**资源包、材质包、光影包、数据包和模组。

```
按 P 键（或输入 /packhub）打开界面
  ├─ 搜索页    资源包 / 材质包 / 光影 / 模组 / 数据包：选源（Modrinth / CurseForge）、搜索、翻页、一键安装
  ├─ 版本页    选择具体版本、自动装前置、安装后自动启用/热重载
  ├─ 已安装    启用 / 停用 / 删除 / 重载本地的模组、资源包、光影、插件
  ├─ 热加载    待生效队列、一键软重启、插件运行时加载/卸载/重载、复制重启命令
  └─ 设置      两个源的地址与 Key、CDN 镜像、并发数、自动依赖、自动热重载、Toast 等
```

## 一、热加载能力边界（先看这一节）

Minecraft 跑在 JVM 上，模组类一经加载就无法卸载，Mixin 也在类加载期完成了字节码织入。所以“热加载普通模组”在技术上不可能**在同一个进程里**完成。PackHub 用三层方案覆盖全部需求，并且对每一层的真实能力都如实标注：

| 内容类型 | 热加载方式 | 是否真的不用重启 |
| --- | --- | --- |
| 资源包 / 材质包 | 改写 `options.resourcePacks` 后调用原版 `reloadResourcePacks()` | ✅ 真热重载，立即生效 |
| 光影包 | 触发原版资源重载 + 反射调用 Iris / Oculus 的重载入口 | ✅ 真热重载，Iris 光影列表立即刷新 |
| 数据包 | 放入当前存档 `datapacks/` | ✅ `/reload` 或 `/datapack enable` 后生效 |
| **PackHub 插件 jar** | 独立 `URLClassLoader` 运行时加载 / 卸载 / 重载 | ✅ 真热加载（见 `docs/plugin-dev.md`） |
| 普通 Fabric / NeoForge 模组 | 下载 → 进“待生效”队列 → **一键软重启**（用同一命令行自动重启进程） | ⚠️ 需要重启进程，但**玩家只需要点一次按钮**，不需要关游戏、找启动器 |

“一键软重启”是怎么做的：PackHub 通过 `ProcessHandle` 读取当前 JVM 的完整启动命令行（java 路径 + 所有参数），
用 `ProcessBuilder` 以相同的参数、相同的工作目录拉起一个新进程，然后退出当前进程。新进程会载入刚下载的模组。
脚本兜底：`config/packhub/restart-minecraft.bat`（Windows）/ `restart-minecraft.sh`。

## 二、安装

1. 安装 **Fabric Loader ≥ 0.19.5**（或 **NeoForge 26.3.0.77+**）和 **Minecraft 26.3**。
2. Fabric 用户还需要 **[Fabric API](https://modrinth.com/mod/fabric-api)**。
3. 把下面这个 jar 丢进 `.minecraft/mods/`：
   - Fabric：`packhub-fabric-1.0.2.jar`
   - NeoForge：`packhub-neoforge-1.0.2.jar`
4. 想要光影生效，Fabric 装 Iris，NeoForge 装 Oculus（PackHub 会自动检测并调用它们的重载接口）。

> 两个 jar 是同一套代码在两个加载器上分别编译的产物，**只用装对应加载器的那个**，不要同时装。

## 三、使用

- 打开：快捷键 **P**（可在“选项 → 按键控制 → PackHub”里改）或命令 **`/packhub`**。
- 搜索页顶部有三个按钮：
  - **类型**：在“资源包/材质 → 光影 → 模组 → 数据包”之间切换；
  - **源**：在 **Modrinth / CurseForge** 之间切换（会记住选择，写进 `preferredSource`）；
  - **搜索**：搜索框留空回车 = 按下载量列出热门内容。
- 每个结果右侧有 **版本** 与 **安装**：`安装` 会直接装最新且适配 26.3 + 当前加载器的版本；`版本` 可以自己挑。
- 安装完成后：
  - 资源包/光影 → 自动热重载；
  - 插件格式 jar → 立即运行时加载；
  - 普通模组 → 进“热加载”页的待生效队列，点 **一键软重启并载入**。
- 依赖：默认自动安装 `required` 前置（会先检查 mods 目录里是否已有同 id 模组），**两个来源都生效**。

### 安装过程：进度条 / 取消 / 完成通知

- 点「安装」后自动打开**安装进度**界面（可在设置里关掉自动打开；主界面底部也有「进度」按钮）：
  - 顶部一条**总进度条**（已下载字节 / 总字节 + 百分比）；
  - 下面每个文件一条进度，写着 `下载中 42%` / `校验中` / `完成` / `失败: 原因` / `已取消`；
  - **点某一行 = 只取消那一个任务**，底部「取消全部安装」= 全部停掉。
- 取消是真正的取消：下载线程每写一块数据检查一次，被取消会删掉半截的 `.part` 文件，
  并且**不重试**（不会像网络错误那样反复试）。
- 装完会弹出通知：`安装完成 · <项目名> <版本> → mods/xxx.jar（需重启生效 / 已热加载）`。
- 全部结束后界面变成结果摘要：`完成 3 · 失败 0 · 已取消 1`，需要重启的话会提示点「软重启并载入」。

### 多人联机：房主下发模组，玩家自动下载，全部就绪后房主重启

主界面底部 **「联机」** 按钮进入联机同步界面：

1. **房主**用 PackHub 装了模组（或手动点「把模组同步给玩家」）→ 自动把清单广播给服务器里的其他玩家；
2. **其他玩家**收到后自动把模组下载到自己的 `mods/`，边下边把进度回报给房主（sha1 校验，已存在且一致的会跳过）；
3. 界面里房主能看到每个玩家的 `下载中 3/7 → 已就绪`；
4. **所有玩家都就绪后**，房主点「全部就绪，重启载入」→ 广播重启 → 房主与已就绪玩家一起软重启，模组生效。
   （想让它自动做，就把 `syncAutoRestart` 改 true。）

细节、信任模型、协议与已知边界见 **[docs/multiplayer-sync.md](docs/multiplayer-sync.md)**。

### 界面自适应

所有界面按当前逻辑分辨率动态排布：底部按钮栏在窗口小 / GUI 缩放很大时**自动换行**，
内容区（列表 / 进度）会自动收缩到按钮栏上方，长文本按宽度截断或折行——
不会再出现「全屏时按钮跑到屏幕外 / 内容被裁掉 / 互相压叠」。

### CurseForge 怎么用

CurseForge 的 API **必须带用户自己的 API Key**，PackHub 不内置也不分发任何 Key：

1. 打开 <https://console.curseforge.com/> → 登录 → **API Keys** → 创建一个 Key；
2. 进游戏 → PackHub → **设置** → 把 Key 粘到 `CurseForge Key` 输入框（也可以直接改 `config/packhub.json` 的 `curseForgeApiKey`）；
3. 点 **测试 CurseForge**，看到“连接成功”即可；搜索页把「源」切到 CurseForge 就能用了。

实现细节与注意点：

- 游戏版本固定走 `gameId=432`（Minecraft），加载器用 `modLoaderType`（Forge=1 / Fabric=4 / Quilt=5 / NeoForge=6）；
- 内容类型用 `classId`：模组 6、资源包 12、光影 6552、数据包 6945、整合包 4471；若某类型在 CF 上归类不一致导致 0 结果，PackHub 会自动去掉 `classId` 再搜一次；
- CurseForge 常常不返回 `downloadUrl`，PackHub 按官方 CDN 规则兜底拼接
  `https://edge.forgecdn.net/files/<fileId/1000>/<fileId%1000>/<fileName>`；
  想走镜像就填 `curseForgeCdnOverride` 或改 `curseForgeFallbackDownloadBase`；
- 作者在 CF 上关掉「允许 API 分发」的模组会返回 403，PackHub 会把这条错误原样显示出来，不会静默失败；
- 文件哈希里 `algo=1` 是 Sha1，PackHub 用它做下载后校验（没有哈希的文件跳过校验）。

### 配置（`config/packhub.json`）

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `apiBase` | `https://api.modrinth.com/v2` | Modrinth API 地址，可换成自建/镜像 |
| `apiBaseFallback` / `useFallbackWhenPrimaryFails` | 同主地址 / true | 主地址失败时自动切换 |
| `cdnBaseOverride` | 空 | 例如填镜像前缀，下载直链会把 `cdn.modrinth.com` 换掉 |
| `preferredSource` | `modrinth` | 默认来源（搜索页的「源」按钮会改它） |
| `enableCurseForge` | true | 关闭后搜索页拒绝使用 CurseForge |
| `curseForgeApiBase` | `https://api.curseforge.com/v1` | CurseForge API 地址 |
| `curseForgeApiKey` | 空 | **必填**才能用 CurseForge（见上） |
| `curseForgeFallbackDownloadBase` | `https://edge.forgecdn.net/files/` | `downloadUrl` 缺失时的兜底直链前缀 |
| `curseForgeCdnOverride` | 空 | CurseForge CDN 镜像前缀 |
| `maxConcurrentDownloads` | 3 | 并发下载数 |
| `downloadRetries` | 3 | 单个下载重试次数（含 HTTP 429 限速自动等待重试） |
| `autoResolveDependencies` | true | 自动安装必选前置 |
| `autoEnableResourcePacks` | true | 资源包安装后自动勾选 |
| `hotReloadAfterInstall` | true | 安装后自动热重载 |
| `reloadShaderLoader` | true | 反射调用 Iris/Oculus 重载 |
| `enablePluginHotLoad` | true | 允许运行时加载插件 jar |
| `allowSoftRestartForMods` / `autoSoftRestart` | true / false | 允许/自动软重启 |
| `watchModsFolder` | true | 监控 mods 目录变化并刷新列表 |
| `filterByCurrentLoader` | true | 只显示适配当前加载器的模组 |
| `showToasts` / `searchPageSize` / `searchIndex` | true / 20 / relevance | 提示、分页、排序 |
| `autoOpenProgressScreen` | true | 开始安装时自动打开进度界面（可在里面取消安装） |
| `showInstallSummary` | true | 装完弹出「装了什么」通知（项目 / 版本 / 落到哪个目录） |
| `enableHostModSync` | true | 联机时作为房主，装了模组就自动把清单广播给其他玩家 |
| `allowServerModPush` | true | 联机时作为玩家，允许房主把模组推给本机自动下载 |
| `syncAutoRestart` | false | 所有玩家都装完后自动重启（默认关，交给房主点按钮） |
| `syncResourcePacks` | false | 同步时是否也带资源包/光影 |
| `syncMaxFileSizeMb` | 200 | 单个文件超过这个大小不下发 |

## 四、从源码构建

需要 **JDK 25**（Minecraft 26.3 使用 Java 25）与 **Gradle 9.8.1**（仓库自带 wrapper）。

```bash
# Linux / macOS
./gradlew build
# Windows
gradlew.bat build

# 只构建 Fabric 端
gradlew :fabric:build
# 只构建 NeoForge 端
gradlew :neoforge:build
# 网络拉不到 NeoForge 依赖时跳过它
gradlew build -Ppackhub.neoforge=false

# 验证（推荐全跑一遍）
gradlew :smoke:logic      # 离线逻辑测试（jar 元数据识别、待重启队列、分面构造…）
gradlew :smoke:mock       # 本地 mock 服务器测试（Modrinth + CurseForge 两条链路）
gradlew :smoke:run        # 真实 API 冒烟测试（需要外网）
gradlew :smoke:verifyAll  # 上面三个一起跑
```

产物：

```
fabric/build/libs/packhub-fabric-1.0.2.jar
neoforge/build/libs/packhub-neoforge-1.0.2.jar
```

> **两个 jar 已经构建好了**：<https://github.com/wuli-luofan/packhub/actions> 里最新一次运行
> （runs/38840490248，结论 `success`）的 **Artifacts → packhub-jars** 可以下载到
> `packhub-fabric-1.0.2.jar` 与 `packhub-neoforge-1.0.2.jar`。
> 本机没法跑 `gradlew build` 只是因为出口网络会掐断 maven.fabricmc.net 的大文件（第七节有实测数据），
> 在能访问该仓库的机器上 `gradlew build` 是正常的。

国内网络说明：
- 构建脚本已在 `settings.gradle` / `build.gradle` 里配置了 **BMCLAPI、腾讯云、阿里云** 镜像；官方仓库只作兜底。
- NeoForge 官方 maven（`maven.neoforged.net`）在国内经常不可达，PackHub 默认优先走 BMCLAPI 镜像；
  如果 NeoForge 依赖仍然拉不下来，用 `-Ppackhub.neoforge=false` 先构建 Fabric 端。
- 运行游戏（`runClient`）需要 Mojang 资源与正版账号，且必须使用 Java 25。

## 五、目录结构

```
packhub/
├── common/                    # 与加载器无关的全部逻辑（两个加载器共用同一份源码）
│   └── src/main/java/com/packhub/
│       ├── PackHub.java               # 内核入口
│       ├── platform/                  # 平台抽象 + slf4j 桥
│       ├── source/                    # 来源枚举（Modrinth / CurseForge）与公共异常
│       ├── http/                      # 两个源共用的 HTTP：重试、限速、下载 + sha1 校验
│       ├── modrinth/                  # Modrinth v2 客户端（纯 Java，可脱离游戏测试）
│       ├── curseforge/                # CurseForge v1 客户端（映射成同一套模型）
│       ├── download/                  # 下载调度、前置解析、目录决策
│       ├── installed/                 # 扫描本地 mods/ 读取模组元数据
│       ├── hotload/                   # 热重载、插件运行时、软重启、目录监控
│       └── ui/                        # 游戏内界面（自绘列表，低版本耦合）
├── fabric/                    # Fabric 入口 + fabric.mod.json
├── neoforge/                  # NeoForge 入口 + neoforge.mods.toml
├── smoke/                     # 离线逻辑测试 + mock 服务器测试 + 真实 API 冒烟测试
├── examples/example-plugin/   # 可热加载插件示例
├── tools/                     # 离线校验脚本、API 桩类、网络诊断工具
└── docs/                      # 插件开发规范、验证记录
```

设计要点：
- **不用 Architectury**：`common` 源码被两个模块直接 `srcDir` 编入各自的 jar，避免跨模块依赖与 shadow 打包带来的版本地狱。
- **两个内容源统一模型**：CurseForge 的返回被映射成 Modrinth 形状的 `ProjectSummary / ProjectVersion / VersionFile`，
  下载器、热加载与界面完全不关心来源差异；再加第三个源也只需要再写一个客户端。
- **UI 自绘**：列表、按钮、滚动条都是自己画，只依赖 `fill` / `drawString` 这类最稳定的接口，降低版本升级时的维护成本。
- **风险接口走反射**：`options.resourcePacks`、`reloadResourcePacks()`、Iris 重载入口、`KeyMapping` 构造、Toast、存档路径、打开文件夹都用反射调用，接口变了只记日志不崩溃。

## 六、可热加载插件

PackHub 定义了一种“不会触碰 Mixin、因此可以真正热加载”的插件格式：jar 内放一个 `packhub-plugin.json`，
主类实现 `com.packhub.hotload.plugin.PackHubPlugin`。插件支持 `onLoad / onClientTick / onResourceReload / onUnload` 生命周期，
可以在运行中加载、卸载、重载，也可以在界面里直接管理。

示例见 [`examples/example-plugin`](examples/example-plugin)，完整规范见 [`docs/plugin-dev.md`](docs/plugin-dev.md)。

## 七、验证记录（哪些跑过、哪些没跑）

### 已实际跑通

1. **离线逻辑测试 16/16 通过**（`gradlew :smoke:logic`）：Fabric / NeoForge / PackHub 插件三种 jar 的元数据识别、
   无法识别时回退文件名、jar 类型判定、`mods/` 与 `mods-disabled/` 扫描、已安装 id 列表、
   待重启队列的去重/移除/持久化/自动建目录、两个源的搜索分面构造、版本兼容与主文件选择、sha1。

2. **Mock 服务器测试 13/13 通过**（`gradlew :smoke:mock`，本地起 `HttpServer`，不需要外网、不需要 API Key）：
   - Modrinth：`/search` 映射、`/project/{id}/version` 映射（含主文件与必选依赖）、下载 + sha1 校验、404 带状态码抛错；
   - CurseForge：`/mods/search` 映射（classId、下载量、加载器索引）、`/mods/{id}/files` 映射
     （游戏版本与加载器拆分、`hashes.algo=1` 取 sha1）、**`downloadUrl` 为 null 时按 `fileId/1000`、`fileId%1000`
     拼兜底直链并成功下载校验**、项目详情映射、403 标记为鉴权问题、缺少 API Key 时给出明确提示；
   - HTTP 层：连续 500 后重试成功、429 按 `Retry-After` 等待后成功。

3. **真实 Modrinth 联网冒烟测试 7/7 通过**（网络可用时）：搜索光影（241 命中）/资源包（4875）/Fabric 模组/NeoForge 模组
   （带 `versions:26.3` + `categories:<loader>` 过滤）、版本查询、真实下载 `FreshAnimations_v1.10.5.zip`
   （645,816 字节）并通过 sha1 校验。完整记录见 [`docs/verification.md`](docs/verification.md)。

4. **全量源码按 Java 25 编译：0 错误**（本地用桩类做的一致性检查）。

### ✅ 两个 jar 已由 CI 真实构建成功

本机因为网络拿不到 Minecraft 工具链（见下），所以改用 GitHub Actions 构建：

**运行记录**：<https://github.com/wuli-luofan/packhub/actions/runs/38840490248> —— `conclusion: success`，
产物 `packhub-jars`（499 KB）内容：

| 文件 | 大小 | 校验过的内容 |
| --- | --- | --- |
| `packhub-fabric-1.0.2.jar` | 184,403 字节 | 88 个条目；含 `fabric.mod.json`（入口 `com.packhub.fabric.PackHubFabric`，`minecraft ~26.3`，`java >=25`）、`com/packhub/**`、图标、中英语言文件 |
| `packhub-neoforge-1.0.2.jar` | 182,643 字节 | 88 个条目；含 `META-INF/neoforge.mods.toml`（入口 `com.packhub.neoforge.PackHubNeoForge`，`neoforge >=26.3`）、`com/packhub/**`、图标、中英语言文件 |
| 两个 `-sources.jar` | 约 90 KB | 源码 jar |

同时 CI 里 **离线逻辑测试 16/16、Mock 服务器测试 13/13 也都是 passed**（每个 Gradle 步骤的结论都是 success）。

产物下载：仓库 → Actions → 对应运行 → 页面底部 **Artifacts → packhub-jars**。

### 仍未验证：只能进游戏才能确认的部分

- 游戏内 UI 的实际显示与交互手感（没有 26.3 的可运行客户端与账号，无法实机测试）；
- 软重启在具体启动器（官方启动器 / HMCL / PCL）下的行为——命令形态不同，所以界面上专门放了「复制重启命令」；
- Iris / Oculus 反射重载入口在 26.3 上的真实方法名（找不到只会记日志，不影响你手动在 Iris 里选光影）；
- CurseForge 需要你自己的 API Key 才能真正拉到数据（代码链路已用本地 mock 服务器验证）。

### 本机构建不了的原因（不影响你拿 jar）

这台机器对 `maven.fabricmc.net` / `maven.neoforged.net` 的**大文件传输会被中途掐断**
（实测：1.5 MB 的 `fabric-loom-1.19.1.jar` 传到 111 KB 后停滞并超时；`fabric-loader`、`fabric-api` 同样失败；
换 Node 的 TLS 栈重试也一样 → 链路问题，不是客户端问题），而国内镜像站没有 `fabric-loom` 与 `fabric-api`。
GitHub 运行器没有这个限制，所以构建放在 CI 做。

```powershell
# 本机自检（桩类只对应旧 API，仅能证明工程内部一致性；真正的编译以 CI 为准）
powershell -ExecutionPolicy Bypass -File .\tools\verify-offline.ps1 `
    -Jdk25 "$env:APPDATA\.minecraft\runtime\java-runtime-epsilon"

# 大文件传输诊断：跑到 111 KB 左右会卡死
java .\tools\network\RangeProbe.java https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.1/fabric-loom-1.19.1.jar
```

排查顺序建议：`config/packhub.json` 的 API 地址是否可达 → 日志里 `[PackHub]` 前缀的输出 →
「热加载」页的 `复制重启命令`。

### 26.3 的 API 迁移（踩过的坑都在这里）

26.1 起 Minecraft **不再混淆**，因此工程里**没有 `mappings`**（留着会直接构建失败）。
其余差异（`GuiGraphics` → `GuiGraphicsExtractor`、`render` → `extractRenderState`、
`drawString` → `text`、SDL 取代 GLFW、`MouseButtonEvent`/`KeyEvent`、`net.minecraft.Util` 被删除、
Fabric API 的 `KeyMappingHelper`/`ClientCommands` 改名等）整理在
[`docs/minecraft-26.3-api-notes.md`](docs/minecraft-26.3-api-notes.md)。

## 八、把工程推到 GitHub（拿 jar 的关键一步）

因为这台机器的 `https://github.com` TLS 被网络中断（但 `api.github.com` 与 `github.com:22` 通），
推送方式与常规做法不同。**完整步骤、三条路线的取舍、报错对照表都在
[`docs/push-to-github.md`](docs/push-to-github.md)**，配套工具在 `tools/github-push/`。

最短路径（只走可达的 API，不需要 git 也不需要代理）：

```powershell
# 1) 先在能打开 GitHub 网页的设备上申请 classic token（勾 repo + workflow）
$env:GITHUB_TOKEN = "ghp_xxx"
# 2) 建仓库
java tools\github-push\GitHubSetup.java repo packhub
# 3) 上传整个工程（含 .github/workflows/build.yml，推上去即自动构建并产出两个 jar）
java tools\github-push\GitHubSetup.java upload 你的用户名/packhub
```

