# tools/lib

`gson-2.11.0.jar` 只是为了 `verify-offline.ps1` 能在**完全离线**的环境里把 common 的代码编过
（工程里的配置读写、Modrinth JSON 解析、插件清单解析都用到 Gson）。

- 版本：2.11.0
- 来源：https://repo1.maven.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar
- 许可：Apache License 2.0（https://github.com/google/gson）

真正的模组构建**不使用**这个 jar —— `gradlew build` 会由 Gradle 自己解析 Gson 依赖
（Fabric/NeoForge 都自带 Gson，模组代码只是编译期引用）。
