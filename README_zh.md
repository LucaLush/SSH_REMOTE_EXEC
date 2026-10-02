# SSH Remote Exec (SSH 桌面快捷助手)

[English](README.md) | [简体中文](README_zh.md) | [开发者架构文档 (doc/)](doc/README.md)

一款轻量、极简且高安全性的 Android 原生远程脚本触发与桌面小部件工具（1x1 Widget）。

用户可在桌面轻触小图标，静默通过 SSH 协议连接指定内网/外网服务器，毫秒级执行网络唤醒（Wake-on-LAN）、Docker 容器启停、服务重启或任意自动化运维命令。

---

## 一、 核心功能特色

* **三层实体解耦管理**：
  * **密钥与凭据库**：支持账号明文密码，以及以 `-----BEGIN OPENSSH PRIVATE KEY-----` 开头的纯文本私钥（支持现代 Ed25519、RSA、ECDSA 等），支持配置私钥口令 Passphrase。
  * **服务器管理**：配置主机 IP/域名、SSH 端口、登录用户名，绑定对应的凭证；支持清晰的认证方式标识（密码/私钥），支持在 App 内一键“测试连接”验证网络握手与鉴权。
  * **命令管理**：定义执行脚本（如 `wol 00:11:22:33:44:55`、`docker restart my-service`）、自定义超时时间、一键执行并弹出控制台回显、一键复制输出与指令。
* **独立桌面小组件 (AppWidget)**：
  * **自由多实例绑定**：桌面可同时添加多个小部件，每个部件绑定不同服务器上的不同命令。
  * **双向添加流程**：支持手机桌面长按添加小部件，或在 App 内点击“添加到桌面”由系统自动 Pin 到手机主屏幕。
  * **微动效与状态回显**：点击小部件即时展示“执行中”蓝色状态；执行成功展示绿色圆勾（800ms 后平滑恢复），失败展示红色感叹号。
* **国际化 (i18n) 支持**：
  * 完美支持中文（简体）与英文界面，根据系统语言自动切换。
* **硬件级高强度加密隔离**：
  * 基于 Android Keystore 与 AES-256-GCM 硬件级加密，本地持久化所有密码及私钥密文，杜绝物理提取泄露风险。
* **零后台常驻与极简权限**：
  * 仅申请 `INTERNET` 与 `ACCESS_NETWORK_STATE` 两个基础权限，无任何常驻 Service，待机电量消耗为 **0%**。

---

## 二、 本地 Linux 编译指南（环境安装与指令）

若你想在本地 Linux（Ubuntu、Debian 或 WSL）上独立编译与运行单元测试，请按照以下步骤安装所需库与工具：

### 1. 安装基础依赖（JDK 17 与网络解压工具）
```bash
sudo apt update
sudo apt install -y openjdk-17-jdk curl unzip git
```

### 2. 下载并配置 Android SDK Command-line Tools
```bash
# 创建 SDK 目录
mkdir -p ~/android-sdk/cmdline-tools

# 下载 Google 官方 Command-line Tools
curl -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q /tmp/cmdline-tools.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
rm -f /tmp/cmdline-tools.zip

# 接受 SDK 许可并安装当前项目所需的 platform-34 与 build-tools 34.0.0
yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
~/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

### 3. 配置项目 SDK 路径与执行编译
进入项目根目录：
```bash
# 指向本地安装的 Android SDK 路径
echo "sdk.dir=$HOME/android-sdk" > local.properties

# 1. 运行自动化单元测试（验证 SSHJ、加密组件与数据库映射）
./gradlew testDebugUnitTest

# 2. 构建可安装的 Debug APK
./gradlew assembleDebug
```
构建产物位置：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 三、 如何清空本地安装的编译环境

如果你以后不再需要本地编译 Android 代码，希望释放磁盘空间并彻底恢复环境，执行以下指令即可：

```bash
# 1. 删除下载安装的 Android SDK 目录（释放约 1.1 GB 磁盘空间）
rm -rf ~/android-sdk

# 2. 删除当前项目中的本地 SDK 指向配置
rm -f local.properties

# 3. （可选）删除 Gradle 构建缓存与守护进程数据（释放约几百 MB ~ 1GB）
rm -rf ~/.gradle

# 4. （可选）如果你不再需要 OpenJDK 17，可从系统卸载
sudo apt remove --purge -y openjdk-17-jdk
sudo apt autoremove -y
```
执行完毕后，系统将彻底恢复为安装前的干净状态。

---

## 四、 GitHub Actions 云端全自动打包指南（免本地配置）

本项目内置了 GitHub Actions CI/CD 流水线（`.github/workflows/build.yml`），无需任何本地环境，推送到 GitHub 即可自动打包。

### 1. 自动构建 Debug APK
1. 每次向 `main` 分支 `push` 代码时，流水线会自动执行质量门禁（单元测试），并在通过后自动生成 Debug APK。
2. 访问仓库首页右侧的 **Releases** 页面即可下载最新的 `app-debug.apk`，也可以在 **Actions** 构建产物 (Artifacts) 中下载。

### 2. 自动构建并签名正式版 Release APK
在仓库 **Settings** -> **Secrets and variables** -> **Actions** 中配置以下 4 个密钥：
* `SIGNING_KEYSTORE_BASE64`：JKS 证书的 base64 文本。
* `KEY_STORE_PASSWORD`：密钥库密码。
* `ALIAS`：证书别名。
* `KEY_PASSWORD`：别名密码。

推送 Tag 触发发布：
```bash
git tag v1.0.0
git push origin v1.0.0
```
流水线将自动编译带有签名的正式版 Release APK，并自动发布在 GitHub Releases。

---

## 五、 核心技术栈与架构

* **编程语言**：Kotlin 1.9 + 协程 (Coroutines)
* **架构模式**：Material Design 3 + ViewBinding + Single Activity Architecture
* **持久化数据库**：Jetpack Room 2.6 (KSP 代码生成)
* **加密凭据**：Android Keystore + AES-256-GCM
* **SSH 核心驱动**：`com.hierynomus:sshj:0.38.0` + BouncyCastle (`bcprov-jdk18on`)
* **桌面小部件**：`AppWidgetProvider` + `RemoteViews` + `requestPinAppWidget`
