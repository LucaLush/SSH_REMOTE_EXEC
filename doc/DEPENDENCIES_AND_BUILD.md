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
