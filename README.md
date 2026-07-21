# Quorvia

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FLecheeel%2FQuorvia.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2FLecheeel%2FQuorvia/refs/branch/master/161d09aec596ffc215f148d2fe61a739f3035767?revisionScanId=113150707)

## 中文

Quorvia 是一个面向 Android 的量子随机现实探索应用原型。它使用 ANU/AQN 量子随机数作为唯一随机源，通过轻量 Node.js 代理保护 API Key，并为后续接入高德地图、定位、路线规划和导航能力预留了现代化 Android 架构。

### 项目目标

- 使用 ANU/AQN Quantum Numbers 作为路线生成的唯一随机源。
- 不使用本机 PRNG/TRNG 或任何随机 fallback。
- 上游量子随机源不可用时，生成流程必须失败关闭。
- Android 端采用 Kotlin、Jetpack Compose、Material 3。
- 后端代理采用 Node.js 24、TypeScript、Fastify。
- 高德 Android SDK 后续负责地图展示、定位、路线规划和导航接入。

### 当前状态

- Android 工程已初始化，包名为 `com.quorvia.app`。
- Compose 首屏已具备探索半径和步行/驾车模式状态。
- Android 本地单元测试覆盖探索 UI 状态边界。
- Node QRNG 代理已支持 `/health` 和 `/v1/qrng`。
- QRNG 代理已对 AQN 响应进行归一化和长度一致性校验。
- Android 设置页已支持隐藏开发者模式，可显式切换 Debug 随机源用于联调。
- 已提供 Debian 13 + systemd 部署模板。
- 已提供根级串行验证脚本。

### 技术栈

- Android: Kotlin, Jetpack Compose, Material 3, Gradle
- Backend: Node.js 24, TypeScript, Fastify, Zod, Undici
- Random Source: ANU/AQN Quantum Numbers
- Map Provider: AMap Android SDK
- Testing: JUnit, Node test runner, TypeScript strict checks

### 验证

在 PowerShell 中运行：

```powershell
.\scripts\verify.ps1
```

该脚本会串行执行：

- Android 单元测试
- Android debug 构建
- Android lint
- Server 测试
- Server 类型检查
- Server 构建
- 敏感值扫描

Android Gradle 任务故意串行运行，避免 Windows 上 Kotlin compiler cache 并发冲突。

FOSSA 许可证检测报告：[查看报告](https://app.fossa.com/projects/git%2Bgithub.com%2FLecheeel%2FQuorvia/refs/branch/master/161d09aec596ffc215f148d2fe61a739f3035767?revisionScanId=113150707)。

### 后端代理

本地启动：

```bash
cd server
npm install
cp .env.example .env
npm run dev
```

接口：

```text
GET /health
GET /v1/qrng?type=uint16&length=4
GET /v1/qrng?type=uint16&length=4&provider=debug
```

如果 ANU/AQN 不可用，代理返回 `502 qrng_unavailable`，不会使用任何本地随机 fallback。
`provider=debug` 只用于移动端联调，服务端必须显式设置 `ALLOW_DEBUG_RANDOM=true` 才会启用；默认关闭并返回 `403 debug_random_disabled`。

### Android

本地构建：

```powershell
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
```

当前使用 compile/target SDK 36。Android lint 会提示 Android 37 和更新 AndroidX 版本，但当前本机 SDK 仓库未暴露 `platforms;android-37`，因此暂时保持 Android 36 可构建线。

设置页连续点击 `About Quorvia` 七次可打开开发者模式；选择 Debug 随机源后，主页面会显示明显调试标识。

### 部署

Debian 13 服务端一键安装或升级：

```bash
curl -fsSL https://raw.githubusercontent.com/Lecheeel/Quorvia/master/server/deploy/install-debian.sh | sudo bash
```

首次安装会提示输入并保存 `AQN_API_KEY`；后续升级会保留现有配置。
服务端默认监听 `0.0.0.0:49030`，需要在云服务器安全组放行 TCP `49030`。

后端部署文档见：

- `server/deploy/README.md`
- `server/deploy/quorvia-qrng.service`

推荐部署到 Debian 13 轻量服务器，使用 systemd 常驻运行，并在前面接 Nginx 或 Caddy 提供 HTTPS。

---

## English

Quorvia is an Android-first quantum-random real-world exploration prototype. It uses ANU/AQN Quantum Numbers as the only randomness source, protects the API key through a lightweight Node.js proxy, and provides a modern Android foundation for future AMap map, location, routing, and navigation integration.

### Goals

- Use ANU/AQN Quantum Numbers as the only randomness source for route generation.
- Do not use local PRNG/TRNG or any random fallback.
- Fail closed when the upstream quantum random source is unavailable.
- Use Kotlin, Jetpack Compose, and Material 3 on Android.
- Use Node.js 24, TypeScript, and Fastify for the backend proxy.
- Use AMap Android SDK for map rendering, location, routing, and navigation handoff.

### Current Status

- Android project initialized with package name `com.quorvia.app`.
- Compose exploration screen includes radius and walk/drive mode state.
- Android unit tests cover exploration UI state boundaries.
- Node QRNG proxy supports `/health` and `/v1/qrng`.
- QRNG responses are normalized and validated for length consistency.
- Android settings include a hidden developer mode for explicitly selecting the Debug random source during integration testing.
- Debian 13 + systemd deployment templates are included.
- A root serial verification script is included.

### Tech Stack

- Android: Kotlin, Jetpack Compose, Material 3, Gradle
- Backend: Node.js 24, TypeScript, Fastify, Zod, Undici
- Random Source: ANU/AQN Quantum Numbers
- Map Provider: AMap Android SDK
- Testing: JUnit, Node test runner, TypeScript strict checks

### Verification

Run from PowerShell:

```powershell
.\scripts\verify.ps1
```

The script runs:

- Android unit tests
- Android debug build
- Android lint
- Server tests
- Server type checks
- Server build
- Sensitive value scan

Android Gradle tasks are intentionally run serially to avoid Kotlin compiler cache contention on Windows.

FOSSA license scan report: [View report](https://app.fossa.com/projects/git%2Bgithub.com%2FLecheeel%2FQuorvia/refs/branch/master/161d09aec596ffc215f148d2fe61a739f3035767?revisionScanId=113150707).

### Backend Proxy

Local startup:

```bash
cd server
npm install
cp .env.example .env
npm run dev
```

Endpoints:

```text
GET /health
GET /v1/qrng?type=uint16&length=4
GET /v1/qrng?type=uint16&length=4&provider=debug
```

If ANU/AQN is unavailable, the proxy returns `502 qrng_unavailable` and does not use any local random fallback.
`provider=debug` is only for mobile integration testing. The server must explicitly set `ALLOW_DEBUG_RANDOM=true`; it is disabled by default and returns `403 debug_random_disabled`.

### Android

Local build:

```powershell
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
```

The project currently uses compile/target SDK 36. Android lint reports Android 37 and newer AndroidX versions, but the local SDK repository does not currently expose `platforms;android-37`, so the project stays on the Android 36 buildable line for now.

Tap `About Quorvia` seven times in Settings to unlock developer mode. When the Debug random source is selected, the main screen shows a clear debug marker.

### Deployment

One-command install or upgrade on Debian 13:

```bash
curl -fsSL https://raw.githubusercontent.com/Lecheeel/Quorvia/master/server/deploy/install-debian.sh | sudo bash
```

The first install prompts for `AQN_API_KEY`; later upgrades keep the existing configuration.
The server binds to `0.0.0.0:49030` by default; allow TCP `49030` in the cloud security group.

Backend deployment docs:

- `server/deploy/README.md`
- `server/deploy/quorvia-qrng.service`

Recommended deployment target: Debian 13 lightweight server, systemd service, and Nginx or Caddy in front for HTTPS.
