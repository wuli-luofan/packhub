# PackHub 插件开发（真正可热加载的模组格式）

普通 Fabric / NeoForge 模组在 JVM 里无法卸载，Mixin 也在类加载期完成织入，所以它们**必须重启进程**才能生效。
PackHub 额外定义了一种插件格式：只要不依赖 Mixin、不改动原版类，它就可以在游戏运行中被**加载、卸载、重载**。
PackHub 会为每个插件建立独立的 `URLClassLoader`，卸载时关闭类加载器，生命周期完全由 PackHub 管理。

## 1. 最小的插件 jar

```
my-plugin.jar
├── packhub-plugin.json
└── com/example/MyPlugin.class
```

`packhub-plugin.json`：

```json
{
  "id": "my-plugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "main": "com.example.MyPlugin",
  "apiVersion": 1,
  "description": "演示插件",
  "authors": ["you"]
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | ✅ | 唯一标识，小写；决定数据目录 `config/packhub/plugins/data/<id>` |
| `main` | ✅ | 实现 `PackHubPlugin` 的类，必须有公开无参构造函数 |
| `name` / `version` / `description` / `authors` | | 界面展示用 |
| `apiVersion` | | 默认 1；大于 PackHub 支持的版本会拒绝加载 |

## 2. 主类

```java
package com.example;

import com.packhub.hotload.plugin.PackHubPlugin;
import com.packhub.hotload.plugin.PluginContext;
import java.nio.file.Files;
import java.nio.file.Path;

public class MyPlugin implements PackHubPlugin {

    private PluginContext context;
    private int ticks;

    @Override
    public void onLoad(PluginContext context) throws Exception {
        this.context = context;
        context.log("已加载，数据目录 %s", context.dataDir());
        Path marker = context.dataDir().resolve("loaded.txt");
        Files.writeString(marker, "loaded at " + System.currentTimeMillis());
    }

    @Override
    public void onClientTick() {
        // 每 tick 调用，务必保持轻量；重活请自己开线程
        if (++ticks % 1200 == 0) {
            context.toast("插件已运行 " + (ticks / 20) + " 秒");
        }
    }

    @Override
    public void onResourceReload() {
        context.log("资源重载了");
    }

    @Override
    public void onUnload() {
        context.log("被卸载/重载/游戏退出，释放资源");
    }

    @Override
    public String statusLine() {
        return "tick=" + ticks;
    }
}
```

## 3. 生命周期与保证

| 回调 | 时机 | 备注 |
| --- | --- | --- |
| `onLoad(PluginContext)` | 加载 / 重载时 | 抛异常会导致加载失败并在界面显示错误 |
| `onClientTick()` | 客户端每 tick | 异常会被捕获、计数，不会拖垮游戏 |
| `onResourceReload()` | 资源包重载后 | 与 PackHub 的热重载一起触发 |
| `onUnload()` | 卸载 / 重载 / 退出前 | 在这里关闭线程、文件句柄、连接 |
| `statusLine()` | 界面刷新时 | 可返回一行状态，异常同样被捕获 |

上下文能力：`dataDir()`、`gameDir()`、`configDir()`、`modsDir()`、`minecraftClient()`（返回 `Object`，需要时自行转型）、`log()`、`toast()`。

> 编译插件时只需要 PackHub 的插件 API（`PackHubPlugin` / `PluginContext` 两个类），
> **不需要** Minecraft 依赖——这也是上下文里不出现 Minecraft 类型的原因。

## 4. 编译与安装

用 PackHub 产出的 jar 作为 `compileOnly` 依赖即可（API 类就在里面）：

```groovy
plugins { id 'java' }
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    compileOnly files('libs/packhub-fabric-1.0.0.jar')   // 或 neoforge 版
}
```

```bash
gradlew jar
```

然后三选一：

1. **游戏内**：把插件当成普通模组从 Modrinth 下载 —— 不行，Modrinth 上没有；请走下面两种。
2. **热加载**：把 jar 丢进 `config/packhub/plugins/`，在 PackHub 的「已安装 → 热加载插件」里点 **重载**（或重启后自动加载）。
3. **运行时加载**：把 jar 放到 `mods/`，PackHub 在安装/扫描时会识别出 `packhub-plugin.json` 并提示这是可热加载插件；
   界面上会把它复制到插件目录并立即加载。

## 5. 可以做 / 不可以做

可以：
- 读写自己的数据目录、配置文件；
- 参与 PackHub 的 tick 与资源重载事件；
- 通过 `minecraftClient()` 拿到客户端实例做只读操作（例如读设置、查当前世界名）；
- 用反射访问其他模组的 API（编译期不依赖它们）。

不可以（这是 JVM 的硬限制，不是 PackHub 的限制）：
- 注册新的方块 / 物品 / 实体等**需要注册表**的内容——这必须在加载器初始化阶段完成；
- 使用 Mixin 或改动原版字节码；
- 期望卸载后类被真正回收——如果插件把静态引用泄漏给了父类加载器（例如注册进全局集合），
  类对象不会被 GC 回收。请把所有状态放在实例字段里，并在 `onUnload()` 里清理。

需要注册内容或 Mixin 的场景，请写成普通模组，用 PackHub 的「一键软重启」完成热加载。
