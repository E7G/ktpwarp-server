# ktpwarp-server

ktpWarp: 课堂派自动签到

特色：

- 无需再忍受课堂派公众号每次近 10 MB 的资源文件加载

- 自动完成数字签到、GPS 签到、签入签出签到，无需人工干预

- 针对二维码签到的高效缓解措施

  - 成功率极高：即使在极端恶劣的网络环境下（客户端、服务端皆双向 500 ms 延迟，丢包率 10%，最短的签到二维码切换间隔，10 位用户），每次提交二维码时也能确保不低于 70% 的成功率

- 支持多用户

- 支持监测互动答题

- 通过 Telegram 机器人、Web 网页前端、iOS MITM 模块与 Android app 与服务器进行交互，可接收签到结果广播，也可进行提交二维码、手动检查、跳过签到等待时间和取消签到等操作

  - Telegram 机器人：已包含在本项目中

  - Web 客户端：[ktpwarp-web](https://github.com/celesWuff/ktpwarp-web)

  - iOS MITM 模块（重定向课堂派扫码结果到 ktpWarp）：[ktpwarp-ios-mitm](https://github.com/celesWuff/ktpwarp-ios-mitm)

  - Android app：[ktpwarp-android](https://github.com/celesWuff/ktpwarp-android)

## 1.3.4-beta 紧急更新说明

目前，由于课堂派 `openapiv100` 后端关闭，1.2.0-beta 至 1.3.3-beta 的版本可能已经无法正常使用。如果您遇到了该问题，请更新至 1.3.4-beta 版本。

1.3.4-beta 签到效率受限制，请参考 https://github.com/celesWuff/ktpwarp-server/issues/11#issuecomment-2106758958 了解详情。

## 注意

ktpWarp 仍处于 Beta 阶段，这代表 ktpWarp 尚未在生产环境中得到大规模的验证。

因此，ktpWarp 仍可能存在着未知的 Bug 或签到“脱靶”。如果您发现了 Bug，欢迎您在 Issues 中报告。

## 限制

课堂派的二维码签到使用了一个疑似 HMAC-SHA1 的签名来验证签到二维码的有效期，因此 ktpWarp 无法自主完成二维码签到，也不能通过已过期的二维码进行签到。

但是，您可以使用上面列出的任何一种交互方式进行签到，仅需进行一次扫码即可为 ktpWarp 系统中的所有用户签到。

Android app 的扫码速度最快，而 iOS MITM 模块可以让您自行选择任何一种扫码工具，您可以使用您手上最快的扫码器，因此首先推荐使用这两种方式。

## 部署

1. 将本仓库 clone 到您的服务器或本地电脑

2. 安装 Node.js 18 或更高版本

3. 运行 `corepack enable`，如果您熟悉 Node.js，也可以选择您喜欢的方式使用 pnpm，或其他包管理器

4. 将 `config.example.json` 复制为 `config.json` 并修改其中的配置

5. 运行 `pnpm install`

6. 运行 `pnpm dev` 在开发模式下启动，检查程序的输出，确保您的配置正确，能够正常登录所有用户，并且可以使用客户端连接，然后按 `Ctrl + C` 停止

7. 运行 `pnpm start` 启动服务，ktpwarp-server 会在后台运行

8. 运行 `pnpm stop` 停止服务

## 飞牛 fnOS（FPK）安装

### 从 GitHub Release 安装（推荐）

在 [Releases](https://github.com/celesWuff/ktpwarp-server/releases/latest) 页面下载 `ktpwarp-server.fpk`（或 `ktpwarp-server_all.fpk`），在飞牛应用中心选择 **手动安装** 即可。

发布由 GitHub Actions 自动构建并更新 **同一个** Release（`latest`）：

- 推送到 `main` / `master`：自动重新打包并覆盖上传 fpk
- 推送版本标签（如 `v1.3.5-beta`）：同样更新该 Release，并将标题改为对应版本号

```bash
# 发版时先改好 package.json / fpk/manifest 的 version，再：
git tag v1.3.5-beta
git push origin main
git push origin v1.3.5-beta
```

标签名建议与 `package.json`、`fpk/manifest` 中的 `version` 一致。本地 `fpk/app/server/` 等构建目录已加入 `.gitignore`，不会进仓库。

### 本地打包

本项目已内置 `fpk/` 目录与打包脚本，可本地生成用于 fnOS 应用中心“手动安装”的 `.fpk` 文件。

1. 在项目根目录执行 `pnpm install`
2. 执行 `pnpm fpk:build`（Windows）或 `pnpm fpk:build:linux`（Linux/macOS / WSL）
3. 生成 `ktpwarp-server.fpk`（同时复制为 `ktpwarp-server_all.fpk` 便于第三方源分发）

注意：

- 请勿用手写 `tar` 直接打包，否则应用中心会提示「不是有效的 fpk 文件」。必须使用 `fnpack build`。
- Linux 打包脚本会自动从 [飞牛 fnpack 下载页](https://static2.fnnas.com/fnpack/) 获取 `fnpack` 可执行文件。

### fnOS 配置界面与 Web 客户端

安装并启动应用后：

- 飞牛桌面 **ktpWarp 配置**：在线编辑并保存 `config.json`（`http://NAS_IP:18451/`）
- 飞牛桌面 **ktpWarp Web 客户端**：内置 [ktpwarp-web](https://github.com/celesWuff/ktpwarp-web)（`http://NAS_IP:18451/web/`），打开后会自动连接本机 WebSocket，无需再填服务器地址

以上与 WebSocket 共用 **同一端口**（默认 `18451`）。修改配置后请在应用中心 **重启** 应用使签到服务加载新配置。

打包时 `pnpm fpk:build` 会从 ktpwarp-web 的 `gh-pages` 分支拉取静态文件到 `fpk/app/web/`。

若提示「端口被占用」：先在应用中心 **停止** 应用，等待几秒后再 **启动**；并确认 `config.json` 里 `WEBSOCKET_SERVER_PORT` 与 manifest 的 `service_port` 一致（默认均为 `18451`），不要仍使用旧的 `11451`/`11452` 或单独的 `18452`。

## 安全提醒

GitHub Actions 仅用于构建不含个人配置的 fpk 安装包，**请勿将 `config.json` 提交到 git**，否则您的课堂派账号密码及 Telegram Bot token 等信息将对所有人可见。安装后在 NAS 的 `@appdata` 目录或应用配置页中填写配置。

## 名字

ktpWarp 是本项目的总称，包括了 ktpwarp-server、ktpwarp-web、ktpwarp-ios-mitm 和 ktpwarp-android。

ktpwarp-server 是项目的核心，它负责与课堂派进行交互，完成签到，它也配备了充当客户端的 Telegram 机器人，供用户进行操作。

其余三个项目均为 ktpwarp-server 的客户端。

ktpwarp-web 是项目的 Web 客户端，您可以在浏览器中通过它查看和管理您的 ktpwarp-server，也可以使用该 Web 客户端来进行扫码，但速度不如另外两个。

ktpwarp-ios-mitm 是项目的 iOS MITM 脚本，它负责将您扫描到的每一个课堂派签到二维码重定向给 ktpwarp-server，同时也提供 Web 界面。

ktpwarp-android 是项目的 Android app，它能够直接进行扫码并将结果提交给 ktpwarp-server。

## 更新日志

### 1.3

- 现在，您可以添加多个 IP 地址前缀，程序会随机选择一个，并且会优先选择不重复的

### 1.2

- 切换到使用 http2 协议的 `openapiv100` 课堂派后端，大幅提升弱网环境（如海外部署）下的签到成功率及速度

- 修复了极小概率下重复提交相同二维码导致的显示问题

- 修复了 GPS 签到默认坐标不正确的问题

### 1.1

- 签到监测机制重做，根除了在部分情况下无法发现新签到的问题

- 客户端重连后，可以查看当前课程的签到历史记录，以及正在执行的签到

- 移除 Telegram bot 烦人的“服务器已重新启动”消息

- Telegram bot 隐私模式，让机器人不响应白名单以外的群组和用户

- 移除“自动发现新课程”功能（因无法确定课程的上下课时间）

- 移除“重启服务器”功能

### 1.0

- 首次发布

## 许可证

MIT License
