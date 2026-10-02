# SSH Remote Exec (SSH 桌面快捷助手)

一款轻量、极简且高安全性的 Android 原生远程脚本触发与桌面小部件工具（1x1 Widget）。

用户可在桌面轻触小图标，静默通过 SSH 协议连接指定内网/外网服务器，毫秒级执行网络唤醒（Wake-on-LAN）、Docker 容器启停、服务重启或任意自动化运维命令。

---

## 一、 核心功能特色

* **三层实体解耦管理**：
  * **密钥与凭据库**：支持账号明文密码，以及以 `-----BEGIN OPENSSH PRIVATE KEY-----` 开头的纯文本私钥（支持现代 Ed25519、RSA、ECDSA 等），可配置私钥口令 Passphrase。
  * **服务器管理**：配置主机 IP/域名、SSH 端口、登录用户名，绑定对应的凭证，支持在 App 内一键“测试连接”验证网络握手与鉴权。
  * **命令管理**：定义执行脚本（如 `wol 00:11:22:33:44:55`、`docker restart my-service`）、自定义超时时间（默认 8s）、一键执行并弹出控制台回显、一键复制输出。
* **独立桌面小组件 (AppWidget)**：
  * **自由多实例绑定**：桌面可同时添加多个小部件，每个部件绑定不同服务器上的不同命令。
  * **双向添加流程**：
    1. **Launcher 添加**：在手机桌面长按添加小部件，自动唤起命令选择界面进行绑定。
    2. **应用内一键添加**：在 App 命令列表点击“添加到桌面”，由系统自动 Pin 到手机主屏幕。
  * **微动效与状态回显**：点击小部件后即时切换为“执行中”蓝色光环状态并弹出 Toast；成功时展示绿色圆勾，失败时展示红色感叹号，并在 3 秒后优雅重置为待命状态。
* **硬件级高强度加密隔离**：
  * 基于 Android Keystore 与 AES-256-GCM 硬件级加密，本地持久化所有密码及私钥密文，杜绝物理提取泄露风险。
* **零后台常驻与极简权限**：
  * 仅申请 `INTERNET` 与 `ACCESS_NETWORK_STATE` 两个基础权限，无任何常驻 Service，待机电量消耗为 **0%**。

---

## 二、 GitHub Actions 云端全自动打包指南

本项目已内置好完整可靠的 GitHub Actions CI/CD 流水线（`.github/workflows/build.yml`），无需在本地安装 GB 级的 Android Studio 或配置繁琐的 Java/Android SDK，推送至 GitHub 即可全自动编译。

### 1. 自动构建 Debug APK
1. 将当前项目初始化并推送到你的 GitHub 仓库：
   ```bash
   git init
   git add .
   git commit -m "feat: initial commit for ssh remote exec app"
   git branch -M main
   git remote add origin https://github.com/<你的用户名>/<你的仓库名>.git
   git push -u origin main
   ```
2. 每次向 `main` 分支 `push` 代码时，GitHub Actions 会在 2~3 分钟内自动完成 JDK 17、Gradle 依赖缓存配置并打包 Debug APK。
3. 打开 GitHub 仓库的 **Actions** 页面，点击最新的构建任务，在下方 **Artifacts** 区域即可直接下载 `SSHRemoteExec-Debug-APK.zip`，解压即为安装包。

---

### 2. 自动构建并签名 Release APK（及自动发布 GitHub Release）

如果你希望打包带官方签名的正式版 Release APK，只需在 GitHub 仓库添加签名密钥：

#### 第一步：生成签名证书（若已有可跳过）
在终端中执行：
```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias wakekey
```

#### 第二步：获取证书的 Base64 编码
```bash
base64 -w 0 release.jks > keystore_base64.txt
```

#### 第三步：配置 GitHub Secrets
打开你的 GitHub 仓库，进入 **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**，添加以下 4 个密钥：
* `SIGNING_KEYSTORE_BASE64`：填入 `keystore_base64.txt` 中的整段文本。
* `KEY_STORE_PASSWORD`：你生成证书时设置的密码。
* `ALIAS`：`wakekey`
* `KEY_PASSWORD`：别名密码（通常与密钥库密码一致）。

#### 第四步：触发正式发布
当你打上版本 Tag 并推送到 GitHub 时：
```bash
git tag v1.0.0
git push origin v1.0.0
```
云端流水线将自动编译 Release APK，完成签名和混淆压缩，并在 GitHub 的 **Releases** 页面自动创建一个发布版本并附带 APK 下载。

---

## 三、 本地手动编译（可选）

若你本地已具备 JDK 17 环境，可在项目根目录下直接执行：
```bash
# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本（需在 app/ 目录下放置 release.jks 或配置环境变量）
./gradlew assembleRelease
```
产物位置：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 四、 核心技术栈与架构

* **编程语言**：Kotlin 1.9 + 协程 (Coroutines)
* **架构模式**：Material Design 3 + ViewBinding + Single Activity Architecture
* **持久化数据库**：Jetpack Room 2.6 (KSP 代码生成)
* **加密凭据**：Android Keystore + AES-256-GCM
* **SSH 核心驱动**：`com.hierynomus:sshj:0.38.0` + BouncyCastle (`bcprov-jdk18on`)
* **桌面小部件**：`AppWidgetProvider` + `RemoteViews` + `requestPinAppWidget`
