# Git 推送规范与手机热点执行方法

本文用于本项目向 GitHub 推送代码时的统一检查与执行。目标是：只推送明确需要的源码和文档，避免运行数据、凭据和构建产物进入仓库；在有线网络保留内网访问的同时，仅让本次 GitHub 推送走手机热点。

## 1. 基本原则

1. 推送前必须明确**源分支**和**目标远程分支**。不要仅凭当前分支名称假定推送目标。
2. 不使用 `git add .` 或 `git add -A` 作为日常推送方式；只暂存明确的源码、配置模板、SQL、测试和已审阅文档。
3. 不提交真实凭据、数据库备份、运行目录、构建产物、安装包、压缩包或 `.bak` 文件。
4. 已经出现在本地未推送提交中的敏感文件，不能靠最后再删除一个提交解决；必须在推送前重写未推送历史，将文件从历史中移除。
5. 除非确认远程历史可被覆盖，否则禁止使用 `git push --force` 或 `--force-with-lease`。

## 2. 推送前检查

在项目根目录执行：

```bash
git status --short
git branch -vv
git remote -v
```

确认目标分支后，检查待推送差异。下面以 `fix/mapping-import-timeout` 为例：

```bash
git fetch origin fix/mapping-import-timeout
git log --oneline origin/fix/mapping-import-timeout..HEAD
git diff --name-status origin/fix/mapping-import-timeout..HEAD
git diff --check origin/fix/mapping-import-timeout..HEAD
```

尤其检查下列内容是否意外出现：

```bash
git diff --name-only origin/fix/mapping-import-timeout..HEAD | \
  rg '(^db-backups/|(^|/)\.env|\.env\.bak$|\.bak$|\.tar\.gz$|\.zip$|\.deb$|/release/|\.apk$)'
```

如需检查特定文件没有进入待推送差异：

```bash
git diff --name-status origin/fix/mapping-import-timeout..HEAD -- \
  '80端口访问技术说明.md' \
  '新更新/network.md'
```

没有输出才表示这些路径没有随本次差异推送。注意：若远程分支本来就跟踪某个文件，`gitignore` 不会停止对该已跟踪文件的后续提交；需要保证该文件在差异中没有变化，或明确提交删除它。

## 3. 暂存和提交规范

只添加明确需要的目录或文件，例如：

```bash
git add backend/src frontend/src deploy/scripts \
  backend/src/main/resources/db/mysql docs
git diff --cached --check
git diff --cached --stat
git commit -m 'feat: 简要说明本次改动'
```

提交前再次检查暂存区：

```bash
git diff --cached --name-only
```

发现误暂存文件时，使用以下命令取消暂存，不会删除本地文件：

```bash
git restore --staged -- <文件路径>
```

## 4. 手机热点推送

### 4.1 首选：手机提供 HTTP/HTTPS 代理

手机热点若提供代理服务，先确认热点网卡、网关和代理端口。只在当前命令中设置代理变量，不修改系统默认路由：

```bash
HTTP_PROXY=http://<热点网关IP>:<代理端口> \
HTTPS_PROXY=http://<热点网关IP>:<代理端口> \
http_proxy=http://<热点网关IP>:<代理端口> \
https_proxy=http://<热点网关IP>:<代理端口> \
git push https://github.com/<组织或账号>/<仓库>.git \
  <本地源分支>:refs/heads/<目标分支>
```

`<本地源分支>` 和 `<目标分支>` 必须写全。例如：

```bash
git push https://github.com/Tthih-Yu/BOX.git \
  push/fix-mapping-sanitized:refs/heads/fix/mapping-import-timeout
```

### 4.2 备用：热点允许 HTTPS，但不提供代理且 SSH 被阻断

本次环境验证过：`ssh.github.com:443` 经热点超时，而 `https://github.com:443` 经热点可用。此时可让 Codex 启动一个临时、本机回环监听、只允许访问 `github.com:443` 的 CONNECT 代理，并把 Git 的代理变量设为其监听地址。

执行时遵循以下约束：

1. 代理只监听 `127.0.0.1`，不得暴露到局域网。
2. 出口必须绑定当前热点网卡，例如 `wlp128s20f3`；热点 IP 变化后先重新检查。
3. 代理仅在推送期间运行，命令结束后立即停止。
4. Git 使用 HTTPS 远程地址，避免 SSH-over-443 被热点阻断。

推送命令的固定形式如下；其中 `127.0.0.1:17891` 为临时代理的示例端口：

```bash
env -u ALL_PROXY -u all_proxy -u NO_PROXY -u no_proxy \
  HTTP_PROXY=http://127.0.0.1:17891 \
  HTTPS_PROXY=http://127.0.0.1:17891 \
  http_proxy=http://127.0.0.1:17891 \
  https_proxy=http://127.0.0.1:17891 \
  git push https://github.com/Tthih-Yu/BOX.git \
  <本地源分支>:refs/heads/<目标分支>
```

GitHub 要求认证时，使用 GitHub 用户名和个人访问令牌（PAT）；令牌只在终端提示时输入，禁止写入命令、文档、日志或聊天记录。

## 5. 推送后验证

推送成功后立即执行：

```bash
git ls-remote --heads https://github.com/Tthih-Yu/BOX.git \
  refs/heads/<目标分支>
```

并在 GitHub 页面确认：

1. 分支名称正确；
2. 最新提交号和本地一致；
3. 文件变更列表不包含备份、环境文件、安装包和未授权文档；
4. CI、构建或部署检查（如有）状态正常。

## 6. 常见故障

| 现象 | 处理方式 |
| --- | --- |
| `ssh.github.com:443` 连接超时 | 不改系统路由；改用热点 HTTPS + 临时本机代理。 |
| `Permission denied (publickey)` | 确认当前 Linux 用户的私钥与 GitHub 账号中添加的公钥匹配。 |
| `non-fast-forward` | 先获取远程最新提交并比较差异；未经确认不要强推。 |
| 推送命令无输出一直等待 | 使用 `ConnectTimeout` 或先用 `curl`/`git ls-remote` 验证代理和热点；必要时 `Ctrl+C` 终止，远程引用在最后一步才更新。 |
| 文件已加入忽略规则却仍被推送 | 该文件已经被 Git 跟踪；用 `git restore --staged` 取消本次暂存，或明确处理已有历史。 |
