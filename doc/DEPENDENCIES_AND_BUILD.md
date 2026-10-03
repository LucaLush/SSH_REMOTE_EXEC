# 依赖清单、代码混淆与构建指南 (Dependencies, ProGuard & Build)

[返回开发文档目录](README.md)

---

## 1. 核心第三方依赖清单与引入理由

| 依赖库与坐标 | 版本 | 核心作用与引入理由 |
| :--- | :--- | :--- |
| `com.hierynomus:sshj` | `0.38.0` | 核心 SSHv2 客户端驱动。全面支持 Session、Channel Exec、端口转发与流式通信。 |
| `org.bouncycastle:bcprov-jdk18on` | `1.78.1` | 加密套件底层提供者。提供现代 Ed25519、X25519、ChaCha20-Poly1305 等非对称算法支持。 |
| `org.bouncycastle:bcpkix-jdk18on` | `1.78.1` | PKCS#8、OpenSSH Key、X.509 证书格式编解码辅助库。 |
| `androidx.room:room-*` | `2.6.1` | 官方 SQLite ORM 框架，采用 KSP（Kotlin Symbol Processing）在编译期生成类型安全的 SQL 映射代码。 |
| `androidx.security:security-crypto` | `1.1.0-alpha06` | Android Keystore 加密封装组件。 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-*` | `1.7.3` | 异步协程调度与响应式轮询驱动。 |
| `com.google.android.material:material` | `1.11.0` | Material Design 3 风格组件库（Cards、Toolbars、Chips、Dialogs）。 |

---

## 2. 代码混淆与 ProGuard 规则保障 (`proguard-rules.pro`)

在 Release 模式构建中，开启了 `isMinifyEnabled = true` 与 `isShrinkResources = true`。为确保 R8/ProGuard 不会将反射调用的加密算法或 Room 实体类混淆，工程配置了以下关键防混淆规则：

1. **BouncyCastle 安全提供者保活**：
   ```proguard
   -keep class org.bouncycastle.** { *; }
   -dontwarn org.bouncycastle.**
   ```
2. **SSHJ 核心网络与鉴权类保活**：
   ```proguard
   -keep class net.schmizz.sshj.** { *; }
   -keep class com.hierynomus.** { *; }
   -dontwarn net.schmizz.sshj.**
   ```
3. **Room 数据库实体与 DAO 保活**：
   ```proguard
   -keep class com.antigravity.sshwake.data.** { *; }
   ```

---

## 3. 自动化单元测试框架 (Unit Testing Architecture)

项目内置了脱离 Android 真机的轻量级单元测试集，位于 `app/src/test/java/`：

```
app/src/test/java/com/antigravity/sshwake/
├── SSHExecutorUnitTest.kt      # 测试 BouncyCastle 注入、纯文本 Ed25519 私钥解析、口令解密验证
├── DatabaseConvertersTest.kt   # 测试 Room 类型转换器（AuthType、KeyType 枚举互转）
└── EntityModelUnitTest.kt       # 测试数据模型数据完整性、默认字段与相等性
```

### 本地执行测试命令
```bash
./gradlew testDebugUnitTest
```
测试报告生成在：`app/build/reports/tests/testDebugUnitTest/index.html`。

---

## 4. 持续集成与质量门禁 (CI/CD Quality Gate)

在 `.github/workflows/build.yml` 中配置了严苛的质量门禁：
1. **测试前置**：在打包任何 APK 之前，首先执行 `testDebugUnitTest`；
2. **失败拦截 (Fail-Fast)**：任何一条单元测试未通过，构建流水线立即熔断，并输出错误用例详情，杜绝构建出带有隐患的损坏包；
3. **制品归档**：无论成功与否，测试报告均自动打包保存至 GitHub Actions Artifacts 中供复盘检查。

---

## 5. Release 正式版本构建规范与注意事项 (Release Build & Distribution Guide)

### 5.1 R8 / ProGuard 混淆避坑指南
在 Release 构建中，启用 `isMinifyEnabled = true` 与 `isShrinkResources = true` 会大幅压缩体积（从 ~14MB 瘦身至 ~5.6MB），但必须注意以下第三方反射机制的保护：
1. **SSHJ & Ed25519 签名库保护**：
   - SSH 握手底层依赖 `net.i2p.crypto.eddsa` 进行 Ed25519 算法签名，该库内部引用了 JDK 私有类 `sun.security.x509.X509Key`；
   - **必配规则**：除 `-keep class net.i2p.crypto.eddsa.** { *; }` 外，必须显式声明 `-dontwarn sun.security.**` 和 `-dontwarn net.i2p.crypto.eddsa.**`，否则 R8 会直接终止编译报 `Missing class sun.security.x509.X509Key` 错误。
2. **BouncyCastle 安全服务提供者保活**：
   - BouncyCastle 依赖大量的字符串反射加载加密算法引擎（如 `X25519`、`Ed25519`、`ChaCha20`），必须全量保留：
     ```proguard
     -keep class org.bouncycastle.** { *; }
     -dontwarn org.bouncycastle.**
     ```
3. **Room 数据库实体与 DAO 映射保活**：
   - Room 生成代码在运行时需要反射获取 DAO 和 Entity 的字段映射，必须保留：
     ```proguard
     -keep class androidx.room.** { *; }
     -dontwarn androidx.room.**
     ```

### 5.2 双轨签名与覆盖安装机制 (Signing Configs)
1. **统一本地与开发签名 (`keystore/debug.keystore`)**：
   - 项目内置固定的 `shared` 证书，密码与别名均为 `android`；
   - 若本地或 CI 环境未配置私有 Release 秘钥，构建系统**自动优雅回退使用 `shared` 证书为 Release APK 签名**；
   - **优势**：保证开发者无论在本地还是云端构建的 Debug/Release 包，签名指纹 100% 相同，手机上测试永远不会触发 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（签名不一致无法更新）。
2. **云端生产正式秘钥注入**：
   - 官方正式发版时，可在 GitHub 仓库的 **Settings -> Secrets** 中配置 `SIGNING_KEYSTORE_BASE64`、`KEY_STORE_PASSWORD`、`ALIAS`、`KEY_PASSWORD`；
   - CI 流水线会自动将其还原为 `app/release.jks` 进行专属正式签名。

### 5.3 跨版本覆盖升级保护 (Version Code Management)
Android 系统的包管理器（PackageManager）严格禁止版本号降级（`INSTALL_FAILED_VERSION_DOWNGRADE`）：
- **设计策略**：采用 `baseOffset + buildNumber` 机制，本地通过 `git rev-list --count HEAD` 计算提交数，云端通过 `GITHUB_RUN_NUMBER`；
- 生产构建设定大基数偏移量（如 `3000`），保证生成的 `versionCode` 严格单调递增，支持手机客户端随时直接覆盖安装升级。

### 5.4 工业级里程碑与 Release 分发规范
在开源与团队协作中，Release 分发严格遵循语义化规范：
1. **日常分支提交 (`push: main`)**：
   - 触发全量测试与打包；
   - 产物上传到 GitHub Actions Artifacts 保存，并在 Release 页面滚动更新最新持续构建包，供测试调试；
2. **正式版本发布 (Milestone Tag `v*`)**：
   - 功能测试稳定后，为当前代码创建语义化 Git Tag（如 `v1.0.0`）：
     ```bash
     git tag -a v1.0.0 -m "Release v1.0.0 - Production Stable Release"
     git push origin v1.0.0
     ```
   - GitHub Actions 监听到 `v*` 标签后，自动编译 Release 与 Debug APK，并调用 Release API 发布为 **正式版（`prerelease: false`）**，标题自动带上里程碑版本号，并自动聚合 Git 提交日志生成 Release Notes。
