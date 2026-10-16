# 把 PackHub 推到 GitHub（本机网络特供版）

这台机器上实测到的网络情况决定了推送方式，所以先说结论：

| 目标 | 实测结果 | 能不能用 |
| --- | --- | --- |
| `https://github.com`（网页 / HTTPS 推送） | TCP 通，但 **TLS 握手被中断**（请求超时） | ❌ 不能用 |
| `https://api.github.com` | 200，正常 | ✅ |
| `github.com:22`（SSH） | TCP 通，SSH 握手到服务器并返回 `Permission denied (publickey)` | ✅ 只差一把已注册的密钥 |
| `ssh.github.com:443`（SSH over 443） | 同上，通 | ✅ 备用通道 |
| `codeload.github.com` / `raw.githubusercontent.com` / `objects.githubusercontent.com` | 全部 200，14.9 MB 只用 4 秒 | ✅ |

所以有三条路，按推荐顺序：

---

## 路线 A：用 API 直接上传（完全绕开 git 与 github.com 的 HTTPS）

仓库里带了工具 `tools/github-push/GitHubSetup.java`，它只走 `api.github.com`。

### 1. 生成一个 Personal Access Token

**这一步必须能打开 GitHub 网页**——可以在一台能上网的设备（手机、另一台电脑）或挂代理的浏览器里做：

- 打开 <https://github.com/settings/tokens> → **Generate new token**
- **classic** token 勾选：`repo` + `workflow`（`workflow` 是必需的，因为要推 `.github/workflows/build.yml`，缺了会报 404/422）
- 复制生成的 `ghp_...`

### 2. 在本机设置环境变量并检查

```powershell
$env:GITHUB_TOKEN = "ghp_你的token"
cd C:\Users\plusone\Documents\deepseek-harness\default-workspace\packhub
java tools\github-push\GitHubSetup.java whoami
```

看到账号名就说明 token 可用。

### 3. 创建仓库并上传

```powershell
# 建一个公开仓库（要私有就加 --private）
java tools\github-push\GitHubSetup.java repo packhub

# 把整个工程（137 个文件，约 0.68 MB）作为一个提交传上去
java tools\github-push\GitHubSetup.java upload 你的用户名/packhub --message "PackHub 1.0.0 初始提交"

# 想先干跑看看会传哪些文件：
java tools\github-push\GitHubSetup.java upload 你的用户名/packhub --dry-run
```

上传完成后打开 `https://github.com/你的用户名/packhub/actions`，`Build PackHub` 工作流会自动开始跑：
先离线逻辑测试与 mock 测试，再 `gradle build`，最后把 `packhub-fabric-1.0.0.jar` 与
`packhub-neoforge-1.0.0.jar` 作为 artifact 上传——**这才是你拿到 jar 的地方**。

---

## 路线 B：本地 git + SSH 推送（有历史、能持续增量提交）

本机没有 git，我已经在工作区装好了便携版 MinGit：

```
C:\Users\plusone\Documents\deepseek-harness\default-workspace\.gittools\mingit\cmd\git.exe
```

工程本身**已经初始化并提交过一次**（commit `6e0205c`，137 个文件，`gradlew` 已标记为可执行）。

### 1. 生成密钥

```powershell
$env:GIT = "C:\Users\plusone\Documents\deepseek-harness\default-workspace\.gittools\mingit\cmd\git.exe"
java tools\github-push\GitHubSetup.java keygen --name packhub
```

记下打印出来的**公钥内容**（`ssh-ed25519 AAAA...`）。

### 2. 把公钥加到 GitHub

二选一：

- **网页**（需要能打开 GitHub）：<https://github.com/settings/keys> → New SSH key → 粘贴公钥；
- **API**（走本机可达的 api.github.com，token 已在路线 A 里设好）：
  ```powershell
  java tools\github-push\GitHubSetup.java addkey --title packhub
  ```

### 3. 建仓库并推送

```powershell
# 建仓库（也可以直接在网页上建）
java tools\github-push\GitHubSetup.java repo packhub

# 让 git 用 Windows 自带的 ssh（MinGit 自带的 ssh 在受限环境下会报 signal pipe 错误）
& $env:GIT config core.sshCommand "C:/Windows/System32/OpenSSH/ssh.exe"

& $env:GIT remote add origin git@github.com:你的用户名/packhub.git
& $env:GIT push -u origin main
```

如果 22 端口哪天不通了，改用 443 通道：

```powershell
& $env:GIT config core.sshCommand "C:/Windows/System32/OpenSSH/ssh.exe -p 443"
& $env:GIT remote set-url origin git@ssh.github.com:你的用户名/packhub.git
& $env:GIT push -u origin main
```

### 4. 提交身份（现在是占位值）

第一次提交用的是 `PackHub <packhub@localhost>`。想换成你自己的：

```powershell
& $env:GIT config user.name "你的名字"
& $env:GIT config user.email "你的邮箱"
& $env:GIT commit --amend --reset-author --no-edit
```

---

## 路线 C：有代理时走标准 HTTPS

如果你有本地代理（Clash / v2ray 等，假设端口 7890），那 `github.com` 的 TLS 问题就绕过去了，
可以直接用最常见的做法：

```powershell
$env:GIT = "C:\Users\plusone\Documents\deepseek-harness\default-workspace\.gittools\mingit\cmd\git.exe"
& $env:GIT config --global http.proxy http://127.0.0.1:7890
& $env:GIT config --global https.proxy http://127.0.0.1:7890

cd C:\Users\plusone\Documents\deepseek-harness\default-workspace\packhub
& $env:GIT remote add origin https://github.com/你的用户名/packhub.git
& $env:GIT push -u origin main
```

推送时会要求认证：用户名填 GitHub 用户名，**密码位置填 Personal Access Token**（GitHub 早就不接受账号密码了）。

---

## 三条路线的取舍

| | 需要 token | 需要网页 | 需要代理 | 保留提交历史 | 后续增量提交 |
| --- | --- | --- | --- | --- | --- |
| A API 上传 | ✅（repo + workflow） | 只在申请 token 时 | ❌ | 每次上传是一个新提交 | 可以，重跑 upload 即可 |
| B SSH 推送 | ❌（用 SSH 密钥） | 只在加公钥时（也可走 API） | ❌ | ✅ | ✅ `git push` |
| C HTTPS + 代理 | ✅ | ❌ | ✅ | ✅ | ✅ |

**只想尽快拿到 jar → 走 A。** 想把仓库当日常开发仓库用 → 先 A 建好仓库，再补 B 的 SSH 密钥持续推送。

---

## 常见报错对照

| 报错 | 原因 | 处理 |
| --- | --- | --- |
| `HTTP 404` / `422`，且这次包含 `.github/workflows/` | token 缺 `workflow` 权限 | 重新生成 classic token，勾上 `workflow` |
| `HTTP 401` | token 拼错 / 已过期 | `whoami` 复核 |
| `HTTP 403` + `rate limit` | 未带 token 时调用频率限制（60/小时） | 带上 token（5000/小时） |
| `git push` 卡住不动 | 走的是 HTTPS 且 `github.com` TLS 被掐 | 换路线 A/B，或给 git 配代理 |
| `Permission denied (publickey)` | 公钥没加到账号，或 git 没用对密钥 | `addkey` 注册；`core.sshCommand` 指定 `-i 私钥路径` |
| MinGit 的 `ssh.exe` 报 `couldn't create signal pipe, Win32 error 5` | 受限环境不允许它创建管道 | `git config core.sshCommand` 指向 Windows 自带 `C:/Windows/System32/OpenSSH/ssh.exe` |

## 附：这些结论是怎么测出来的

```powershell
# 便携版 git 已就位
& "...\.gittools\mingit\cmd\git.exe" --version     # git version 2.56.0.windows.2

# SSH 通道可用（返回 publickey 拒绝即为握手成功）
& "C:\Windows\System32\OpenSSH\ssh.exe" -T git@github.com
#   git@github.com: Permission denied (publickey).

# HTTPS 网页不可用（TCP 通、TLS 被中断）
#   https://github.com/  -> SocketTimeoutException: Connect timed out

# API 与资源域名可用
#   https://api.github.com/...        -> 200
#   https://codeload.github.com/...   -> 200（14.9 MB / 4 s）
```
