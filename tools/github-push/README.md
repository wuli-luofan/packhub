# tools/github-push —— GitHub 推送助手

本机实测：`https://github.com` 的 TLS 握手被网络中断（网页 / HTTPS 推送不可用），
但 `api.github.com` 与 `github.com:22`（SSH）是通的。这个工具只走 API，因此在当前网络下依然能建仓库、传代码、注册 SSH 公钥。

完整操作步骤（含三种推送路线与报错对照）见 [`../../docs/push-to-github.md`](../../docs/push-to-github.md)。

## 快速用法

```powershell
cd <工程根目录>            # 也就是含 settings.gradle 的 packhub 目录
$env:GITHUB_TOKEN = "ghp_xxx"      # classic token，需要 repo + workflow 权限

java tools\github-push\GitHubSetup.java whoami                     # 验证 token
java tools\github-push\GitHubSetup.java repo packhub               # 创建仓库（--private 建私有）
java tools\github-push\GitHubSetup.java keygen --name packhub      # 生成 SSH 密钥（默认 ~/.ssh）
java tools\github-push\GitHubSetup.java addkey --title packhub     # 把公钥注册到账号
java tools\github-push\GitHubSetup.java upload 用户名/packhub --dry-run   # 干跑，看会传哪些文件
java tools\github-push\GitHubSetup.java upload 用户名/packhub      # 真正上传（Git Data API）
```

可选项：`--branch main`、`--message "提交信息"`、`--project <packhub 目录>`、`--token <token>`。

## 它做了什么

| 子命令 | 调用的接口 |
| --- | --- |
| `whoami` | `GET /user` |
| `repo` | `POST /user/repos` |
| `addkey` | `POST /user/keys` |
| `upload` | `POST /git/blobs`（逐文件）→ `POST /git/trees` → `POST /git/commits` → `POST/PATCH /git/refs` |

`upload` 会自动跳过 `.git/`、`build/`、`.gradle/`、`run/`、`.idea/`、`out/`，
并把 `gradlew` 标记为可执行（mode `100755`），这样在 Linux CI 上也能 `./gradlew`。

`keygen` 不依赖 `ssh-keygen` 写文件的能力：如果 `.pub` 没写出来（受限环境常见），
工具会用纯 Java 解析 OpenSSH 私钥容器把公钥导出来（已与 `ssh-keygen -y` 的结果逐字节比对过）。

## 不需要 token 的部分

`upload --dry-run`、`keygen` 都能离线跑，可以先验证环境。
