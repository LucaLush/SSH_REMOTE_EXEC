# SSH Remote Exec 核心开发者文档索引 (Developer Documentation)

欢迎查阅 `SSH Remote Exec` 的工程架构与深度开发文档。本目录为后续功能演进、二次开发与架构维护提供详尽的技术参考。

---

## 📚 文档目录

| 文档模块 | 核心内容概述 |
| :--- | :--- |
| 🏗️ **[系统架构设计与数据流 (ARCHITECTURE.md)](ARCHITECTURE.md)** | • 核心分层架构（UI、持久层、SSH 驱动层、安全隔离层）<br/>• 三层解耦实体模型（凭证库、服务器、命令）与 ER 关系<br/>• Android Keystore 硬件级 AES-256-GCM 加密与解密全流程<br/>• 桌面小部件 (AppWidget) 双向绑定机制与状态机流转 |
| 🔌 **[SSH 对接与执行引擎深度解析 (SSH_INTEGRATION.md)](SSH_INTEGRATION.md)** | • SSHJ 0.38.0 与 BouncyCastle 1.78.1 技术选型理由<br/>• BouncyCastle 动态安全提供者注入机制<br/>• 纯内存私钥字符串解析（杜绝 ENOENT 磁盘路径陷阱）<br/>• **核心算法**：反应式非阻塞轮询（彻底解决后台子进程卡死与假超时） |
| 🧵 **[线程模型与组件生命周期 (THREADING_AND_LIFECYCLE.md)](THREADING_AND_LIFECYCLE.md)** | • Kotlin 协程调度模型（`Dispatchers.Main` 与 `Dispatchers.IO` 分工）<br/>• `SSHWidgetProvider` 协程作用域与 `SupervisorJob` 容错隔离<br/>• **核心复盘**：桌面小控件防并发与 AMS 广播队列防排队机制<br/>• ViewBinding 释放防内存泄露规范与 AlarmManager 硬件保活 |
| 📦 **[依赖清单、代码混淆与构建指南 (DEPENDENCIES_AND_BUILD.md)](DEPENDENCIES_AND_BUILD.md)** | • 核心第三方依赖库版本清单与引入理由<br/>• **Release 构建规范**：R8 / ProGuard 混淆避坑（Ed25519 & Sun 警告压制）<br/>• 双轨签名机制、版本号单调递增管理与语义化里程碑发布规范<br/>• 自动化本地单元测试架构与 CI/CD 质量门禁设计 |

---

## 🚀 快速二次开发指引

### 1. 修改/添加数据库字段
- 数据库定义在 `app/src/main/java/com/antigravity/sshwake/data/`；
- 修改实体类（`ServerEntity`、`KeyEntity`、`CommandEntity`）后：
  1. 在 `AppDatabase.kt` 中递增 `version` 版本号；
  2. 提供对应版本的 `Migration` 或升级逻辑；
  3. 执行 `./gradlew testDebugUnitTest` 自动验证 Room 编译代码生成。

### 2. 调整 SSH 交互逻辑
- 核心代码集中在 `app/src/main/java/com/antigravity/sshwake/ssh/SSHExecutor.kt`；
- 每次调整流读取或超时等待机制时，务必执行单元测试验证私钥解析：
  ```bash
  ./gradlew testDebugUnitTest --tests com.antigravity.sshwake.SSHExecutorUnitTest
  ```

### 3. 调试桌面小部件
- 小部件相关代码位于 `app/src/main/java/com/antigravity/sshwake/widget/`；
- `SSHWidgetProvider.kt`：处理点击事件与广播接收；
- `WidgetManager.kt`：管理 SharedPreferences 绑定与 `RemoteViews` 视图渲染。
