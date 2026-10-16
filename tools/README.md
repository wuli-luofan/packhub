# tools 目录说明

这里的两个工具都是**离线/诊断用途**，不参与模组构建（`settings.gradle` 里没有把它们做成子项目）。

## api-stubs —— 离线内部一致性检查

`api-stubs/` 里是为 PackHub 用到的那部分 Minecraft / Fabric / NeoForge API 手写的**桩类**。
在没有网络、拉不到 Minecraft 工具链的机器上，可以用它把整个工程编译一遍，
确认**工程自身的代码没有错误**（漏 import、调用不存在的自有方法、泛型/重载错误等）。

用 `verify-offline.ps1` 一键跑（Windows PowerShell 默认禁止运行脚本，需要加 `-ExecutionPolicy Bypass`；
Gson 已经放在 `tools/lib/` 下，脚本会自动使用，无需额外参数）：

```powershell
# 用随游戏分发的 JDK 25（路径按你的实际情况改）
powershell -ExecutionPolicy Bypass -File .\tools\verify-offline.ps1 `
    -Jdk25 "$env:APPDATA\.minecraft\runtime\java-runtime-epsilon"
```

脚本会输出编译诊断条数，期望是 `0`。本机实测结果：

```
Compiling 98 source files (JDK 25 + API stubs)...
Diagnostics: 0  (expected 0)
PASS: 127 class files produced.
```

> ⚠️ **这些桩类的签名是我根据 1.21.x 的 API 手写的，不代表 Minecraft 26.3 的真实签名。**
> 它们只能证明内部一致性，**不能**替代 `gradlew build`。真正的 API 校验必须在能下载依赖的机器上做。

## network/RangeProbe.java —— 大文件传输诊断

用来判断某个 maven 仓库是不是「小文件能下、大文件被掐断」：

```powershell
java tools\network\RangeProbe.java https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.1/fabric-loom-1.19.1.jar
```

输出会分别尝试两次分段请求和一次完整下载，并每 2 秒打印已下载字节数。
如果完整下载停在某个字节数后超时，说明是网络链路限制（本次交付的机器就是这个情况，见
[`../docs/verification.md`](../docs/verification.md) 第 3 节）。
