# SSH 对接与执行引擎深度解析 (SSH Integration & Execution Engine)

[返回开发文档目录](README.md)

---

## 1. 技术选型：为什么选择 SSHJ 与 BouncyCastle？

在 Android 平台上实现原生 SSH 协议通信，业内常见方案有 JSch、SSHJ 与 Apache Mina SSHD。本项目选用 **SSHJ 0.38.0 + BouncyCastle 1.78.1**，原因如下：

| 对比维度 | JSch (传统方案) | SSHJ (本项目采用) |
| :--- | :--- | :--- |
| **算法支持** | 较老旧，默认不支持现代 OpenSSH 密钥格式与 Ed25519/Curve25519 | 全面支持现代 `ssh-ed25519`、`ecdsa-sha2-nistp256`、`rsa-sha2-512` 等现代加密套件 |
| **API 现代性** | 2018 年后基本停滞维护 | 活跃维护，提供强类型 Session、Channel 与流控 API |
| **安全密钥解析** | 需临时落地到文件或自定义复杂流解析 | 原生提供基于内存字符串解析的 `KeyProvider` 重载 |

---

## 2. BouncyCastle 动态安全提供者注入

Android 系统自带的 AndroidOpenSSL / Conscrypt 对某些特定非对称曲线（如 Ed25519 签名算法）缺少原生 JCE Provider 支持。若未注册 BouncyCastle，在解析私钥或握手时会抛出：
`java.security.NoSuchAlgorithmException: KeyFactory Ed25519 implementation not found`

### 解决机制
在 `SSHExecutor` 单例初始化以及 App 启动时，强制将 `BouncyCastleProvider` 注入到安全提供者列表第 1 位：
```kotlin
init {
    try {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    } catch (_: Exception) {
    }
}
```
并且在单元测试 `SSHExecutorUnitTest` 中设有基准测试，确保无论环境如何迁移，该 Provider 必定就绪。

---

## 3. 内存字符串私钥解析（杜绝 ENOENT 磁盘陷阱）

### 问题背景
SSHJ 的 `loadKeys(String)` 方法在早期设计中，如果传入单个字符串，会优先尝试将其当作**本地文件路径**调用 `new File(location)`。在 Android 上如果直接传入私钥文本，会报错：
`java.io.FileNotFoundException: open failed: ENOENT (No such file or directory)`

### 内存纯文本解析方案
通过调用其 3 参数重载方法，显式声明不走文件加载：
```kotlin
fun createKeyProvider(
    client: SSHClient,
    privateKeyContent: String,
    passphrase: String? = null
): KeyProvider {
    // 1. 标准化文本：确保私钥文本以换行符结尾，防止 Base64 截断
    val normalizedKey = if (privateKeyContent.endsWith("\n")) privateKeyContent else "$privateKeyContent\n"

    // 2. 口令处理器 (若私钥加密则传入口令，若未加密则传 null)
    val passwordFinder = if (!passphrase.isNullOrBlank()) {
        PasswordUtils.createOneOff(passphrase.toCharArray())
    } else {
        null
    }

    // 3. 传入 null as String? 强制指定第二个参数为 passphrase 字符串重载，使第一个参数作为纯内存密钥格式解析
    return client.loadKeys(
        normalizedKey,
        null as String?,
        passwordFinder
    )
}
```
**安全收益**：私钥从未被写入 App 私有目录或 `/data/data/...` 磁盘中，完全常驻于受保护的 RAM 内存中，进程退出或垃圾回收后即刻清空。

---

## 4. 关键演进：反应式轮询（解决后台子进程超时卡死）

### 经典陷阱：为什么简单的脚本很快，调用子脚本/发包很慢甚至超时？
早期版本采用 `cmd.join(timeoutSeconds, TimeUnit.SECONDS)`。
- `cmd.join()` 的底层语义是：**一直阻塞等待 SSH 服务器关闭该 Channel 的 stdout/stderr 流（即 EOF）**。
- 如果你的 Linux 脚本中执行了：
  - 调用了其他命令或脚本；
  - 启动了后台守护进程（如 `python script.py &` 或发送网络包的后台任务）；
  - 子进程继承了父进程的文件描述符（File Descriptors 1 和 2）；
- **即便主脚本已经迅速 `exit 0` 执行完毕，因为后台子进程持有着输出通道，SSH 远端 Channel 永远不会发送 EOF**！
- 结果：客户端一直干等满整个 `timeoutSeconds`，最终抛出 `ConnectionException: Timeout expired`，给用户造成“脚本执行极慢或者失败”的假象。

### 反应式轮询算法设计与实现
项目在 `SSHExecutor.kt` 中重构为基于 `exitStatus` 的非阻塞反应式轮询：

```mermaid
flowchart TD
    Start[session.exec(command)] --> Loop[每 50ms 轮询检测 cmd.exitStatus]
    Loop --> CheckExit{exitStatus != null 或 !cmd.isOpen ?}
    CheckExit -- 否 --> CheckTimeout{当前时间 > deadline ?}
    CheckTimeout -- 否 --> Sleep[delay 50ms] --> Loop
    CheckTimeout -- 是 --> MarkTimeout[标记 timedOut = true] --> EndLoop
    CheckExit -- 是 --> Buffer[若 !cmd.isEOF 则延时 50ms 缓冲收取最后输出] --> EndLoop[跳出等待循环]

    EndLoop --> ReadStreams[readStream: 非阻塞读取可用缓冲数据]
    ReadStreams --> Evaluate[判定成功状态]

    Evaluate --> Cond1{exitStatus == 0 ?}
    Cond1 -- 是 --> Success[判定为执行成功]
    Cond1 -- 否 --> Cond2{!timedOut 且 exitStatus == null ?}
    Cond2 -- 是 --> Success
    Cond2 -- 否 --> Fail[判定为失败或超时]
```

### 核心代码片段
```kotlin
val deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L)
while (System.currentTimeMillis() < deadline) {
    if (cmd.exitStatus != null || !cmd.isOpen) {
        break
    }
    delay(50)
}

// 主命令已退出但流尚未 EOF 时，给予 50ms 缓冲吸收最后几行回显
if (cmd.exitStatus != null && !cmd.isEOF) {
    delay(50)
}

val timedOut = (cmd.exitStatus == null && cmd.isOpen)
val isEOF = cmd.isEOF
val stdout = readStream(cmd.inputStream, isEOF)
val stderr = readStream(cmd.errorStream, isEOF)
val exitStatus = try { cmd.exitStatus } catch (_: Exception) { null }

val isSuccess = when {
    exitStatus == 0 -> true
    !timedOut && exitStatus == null -> true
    else -> false
}
```

### 推荐脚本写法
若编写的是常驻服务或无需保留输出的后台唤醒发包命令，在 Linux 脚本端重定向输出为最佳实践：
```bash
/usr/local/bin/wake_packet.sh 192.168.1.100 > /dev/null 2>&1 &
```
即使不重定向，本 App 的轮询引擎也能在主进程退出时立即（几十毫秒内）完成响应！
