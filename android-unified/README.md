# ktpWarp Unified Android

将以下两个上游项目原样组合成单一 Android APK：

- Android: celesWuff/ktpwarp-android @ 7bb7d9c682173d608eae670a995fed1dfbaf1549
- Server: celesWuff/ktpwarp-server @ 5904e68163973e7c5af2d9b988993d4426927411

## 原则

不重写 ktpwarp-server 的签到、二维码、多用户、GPS、IP 前缀、互动答题、Telegram、WebSocket 等业务逻辑。
APK 内嵌 Node.js 18，并直接运行原 server 的 TypeScript 源码。Android 仅新增：

1. 独立的 `:ktpwarp_server` 前台服务进程；
2. config.ts 编辑入口；
3. 启动/停止内置 server；
4. Android 客户端连接本机 WebSocket；
5. GitHub Actions 一键生成可安装 debug APK。

远程 ktpwarp-server 连接方式仍保留，因此一个 APK 同时支持“本机一体化”和“连接外部 server”。

## 使用

首次安装后打开“内置服务器”卡片，点“编辑配置”，按原 ktpwarp-server 的 config.example.ts 填写配置，并确认 WebSocket 地址与 config.ts 中的端口、路径、TLS 设置一致，然后点“保存并启动”。

默认本机地址：

`ws://127.0.0.1:11451/kfccrazythursdayvme50`

## 构建

运行 GitHub Actions: **Build unified Android APK**。

Actions 会固定拉取上述两个上游 commit，将 Node.js Mobile v18.20.4 与 server 打入 Android APK，再输出已签名的 debug APK 供安装测试。
