# 系统架构设计与数据流 (Architecture & Data Flow)

[返回开发文档目录](README.md)

---

## 1. 架构总览

`SSH Remote Exec` 采用 **Material Design 3 + Single Activity + Jetpack Room + Coroutines** 的现代原生 Android 架构。项目划分为以下核心层级：

```mermaid
graph TD
    UI[UI 展现层<br/>MainActivity + 3 Fragments + Dialogs] -->|ViewModel / LiveData / Coroutines| DataLayer[数据持久层<br/>AppDatabase / Room DAOs]
    Widget[桌面小部件层<br/>SSHWidgetProvider + RemoteViews] -->|AppWidgetManager| DataLayer
    Widget -->|触发命令| SSHCore[SSH 核心执行层<br/>SSHExecutor]
    UI -->|测试/手动执行| SSHCore
    SSHCore -->|读取与解密| Security[安全隔离层<br/>CryptoHelper / Android Keystore]
    Security -->|AES-256-GCM| DataLayer
    SSHCore -->|SSHJ + BouncyCastle Socket| RemoteServer[远程服务器 / SSHD]
```

---

## 2. 三层解耦实体设计 (Decoupled Entity Model)

项目设计摒弃了“服务器配置硬编码凭据与命令”的紧耦合方案，采用高内聚、低耦合的三层实体模型：

```mermaid
erDiagram
    KeyEntity ||--o{ ServerEntity : "1 对 N (多台服务器共用同一密钥/密码)"
    ServerEntity ||--o{ CommandEntity : "1 对 N (同一服务器可配置多个独立运维命令)"
    CommandEntity ||--o{ AppWidget : "1 对 N (同一命令可多次 Pin 到不同屏幕)"

    KeyEntity {
        string id PK "UUID"
        string name "凭证别名 (如 My-Ed25519)"
        string type "PASSWORD / PRIVATE_KEY"
        string encryptedSecret "AES-256-GCM 加密后的密文"
        string encryptedPassphrase "AES-256-GCM 加密的私钥口令"
        long createdAt "时间戳"
    }

    ServerEntity {
        string id PK "UUID"
        string name "服务器别名 (如 HomeLab-NAS)"
        string host "主机 IP 或域名"
        int port "SSH 端口 (默认 22)"
        string username "用户名 (如 root)"
        string authType "PASSWORD / KEY"
        string keyId FK "关联的凭证 ID"
        long createdAt "时间戳"
    }

    CommandEntity {
        string id PK "UUID"
        string name "命令别名 (如 唤醒台式机)"
        string serverId FK "关联的服务器 ID (级联删除)"
        string command "Bash / Shell 脚本内容"
        int timeoutSeconds "超时阈值 (默认 8s)"
        long createdAt "时间戳"
    }
```

### 实体间级联删除机制
在 `CommandEntity` 中定义了 Room 的级联外键：
```kotlin
ForeignKey(
    entity = ServerEntity::class,
    parentColumns = ["id"],
    childColumns = ["serverId"],
    onDelete = ForeignKey.CASCADE
)
```
- **删除服务器时**：数据库自动级联删除其下的所有命令，避免产生孤立悬空命令。
- **删除凭据时**：保留关联服务器，界面提示用户重新关联有效凭证，防止误删服务器导致历史命令丢失。

---

## 3. 安全隔离层：硬件级 Android Keystore 加密

针对私钥与明文密码的存储安全，项目拒绝采用明文或对称硬编码密钥方案，实现了严格的硬件级加密隔离：

### 加密流水线流程
```mermaid
sequenceDiagram
    participant User as 用户输入
    participant Crypto as CryptoHelper
    participant Keystore as Android Keystore (TEE / SE)
    participant DB as Room Database (SQLite)

    Note over User,DB: 存储凭证流程
    User->>Crypto: 传入明文密码 / OpenSSH 私钥
    Crypto->>Keystore: 获取 / 生成 AES-256 主密钥 (Alias: SSHRemoteExecMasterKey)
    Crypto->>Crypto: 生成 12 字节高强度随机 IV
    Crypto->>Crypto: 执行 AES/GCM/NoPadding 加密 + 128-bit 认证标签
    Crypto->>Crypto: 打包 [4 字节 IV 长度 + IV + 密文]
    Crypto->>DB: 存储 Base64 编码密文

    Note over User,DB: 读取并解密流程 (仅在建立连接瞬间)
    DB->>Crypto: 读取 Base64 编码密文
    Crypto->>Crypto: 解包提取 IV 与密文字节
    Crypto->>Keystore: 调用硬件密钥执行 GCM 解密与完整性校验
    Crypto->>User: 内存中短暂保留明文用于认证，不落盘
```

### 核心安全特性
1. **AES-256-GCM 认证加密**：不仅具备保密性，而且 128-bit Authentication Tag 保证密文若被物理篡改，解密时将立即抛出异常并失败。
2. **IV 动态生成**：每次加密均由底层 CSPRNG 随机生成 12 字节独立 IV，防止重放攻击和已知明文分析。
3. **内存即用即毁**：解密出的明文字符串仅在 SSH 鉴权瞬间在 RAM 中使用，绝不落盘写入本地任何文件或缓存。

---

## 4. 桌面小部件 (AppWidget) 架构

### 双向绑定设计
1. **系统桌面选择小组件**：
   - 触发 `SSHWidgetConfigureActivity`；
   - 用户从 RecyclerView 中点击目标命令；
   - `WidgetManager.saveBinding(context, appWidgetId, commandId)` 将绑定写入 SharedPreferences；
   - 更新桌面小部件为 IDLE 态。
2. **应用内一键添加 (Pin to Home)**：
   - 使用 Android 8.0+ 的 `AppWidgetManager.requestPinAppWidget`；
   - 携带 `PENDING_COMMAND_ID` 的 `PendingIntent` 作为成功回调；
   - 桌面创建图标后触发广播完成自动绑定。

### 状态机流转 (State Transition)
```mermaid
stateDiagram-v2
    [*] --> IDLE : 添加小部件 / 重置完成
    IDLE --> RUNNING : 用户点击桌面小部件 (显示蓝色旋转图标)
    RUNNING --> SUCCESS : SSH 执行返回 0 (显示绿色对勾)
    RUNNING --> ERROR : 网络握手失败 / 超时 / 非 0 (显示红色感叹号)
    SUCCESS --> IDLE : 延时 800ms 自动复原
    ERROR --> IDLE : 延时 2000ms 自动复原
```
