package com.antigravity.sshwake.ssh

import com.antigravity.sshwake.data.AuthType
import com.antigravity.sshwake.data.KeyEntity
import com.antigravity.sshwake.data.KeyType
import com.antigravity.sshwake.data.ServerEntity
import com.antigravity.sshwake.security.CryptoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.password.PasswordUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.InputStream
import java.security.Security
import java.util.concurrent.TimeUnit

data class SSHResult(
    val isSuccess: Boolean,
    val exitCode: Int? = null,
    val output: String = "",
    val errorMessage: String? = null
)

object SSHExecutor {

    init {
        try {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        } catch (_: Exception) {
        }
    }

    suspend fun executeCommand(
        server: ServerEntity,
        key: KeyEntity?,
        command: String,
        timeoutSeconds: Int = 8
    ): SSHResult = withContext(Dispatchers.IO) {
        val client = SSHClient()
        try {
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connectTimeout = 10000
            client.timeout = ((timeoutSeconds + 10) * 1000).coerceAtLeast(20000)

            // 连接目标主机
            client.connect(server.host, server.port)

            // 认证鉴权
            authenticate(client, server, key)

            val session = client.startSession()
            try {
                val cmd = session.exec(command)
                val deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L)
                while (System.currentTimeMillis() < deadline) {
                    if (cmd.exitStatus != null || !cmd.isOpen) {
                        break
                    }
                    delay(50)
                }

                // 若主进程已退出但流尚未EOF，给予短暂缓冲期（50ms）收取残留输出
                if (cmd.exitStatus != null && !cmd.isEOF) {
                    delay(50)
                }

                val timedOut = (cmd.exitStatus == null && cmd.isOpen)

                val isEOF = cmd.isEOF
                val stdout = readStream(cmd.inputStream, isEOF)
                val stderr = readStream(cmd.errorStream, isEOF)
                val exitStatus = try { cmd.exitStatus } catch (_: Exception) { null }

                val fullOutput = buildString {
                    if (stdout.isNotBlank()) append(stdout)
                    if (stderr.isNotBlank()) {
                        if (isNotEmpty()) append("\n[STDERR]\n")
                        append(stderr)
                    }
                }.trim()

                // 若主进程已退出且退出代码为 0，即使后台子进程未完全关闭输出流，也判定为主命令成功
                val isSuccess = when {
                    exitStatus == 0 -> true
                    !timedOut && exitStatus == null -> true
                    else -> false
                }

                val defaultSuccessMsg = if (timedOut) {
                    "执行成功（主命令已退出，后台子进程未关闭输出通道）"
                } else {
                    "执行成功（命令无回传输出）"
                }

                val errorMessage = when {
                    isSuccess -> null
                    timedOut -> "执行等待超时（已等待 ${timeoutSeconds} 秒）。若命令启动了后台进程，建议在脚本末尾重定向输出：> /dev/null 2>&1 &"
                    else -> "进程退出代码: $exitStatus"
                }

                SSHResult(
                    isSuccess = isSuccess,
                    exitCode = exitStatus,
                    output = fullOutput.ifBlank { defaultSuccessMsg },
                    errorMessage = errorMessage
                )
            } finally {
                session.close()
            }
        } catch (e: Exception) {
            SSHResult(
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "未知连接或执行异常",
                output = "异常详情: ${e.javaClass.simpleName}: ${e.message}"
            )
        } finally {
            try {
                client.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun testConnection(server: ServerEntity, key: KeyEntity?): SSHResult = withContext(Dispatchers.IO) {
        val client = SSHClient()
        try {
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connectTimeout = 5000
            client.timeout = 5000
            client.connect(server.host, server.port)

            authenticate(client, server, key)

            SSHResult(isSuccess = true, output = "SSH 连接与认证成功")
        } catch (e: Exception) {
            SSHResult(isSuccess = false, errorMessage = e.localizedMessage ?: "连接失败")
        } finally {
            try {
                client.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun authenticate(client: SSHClient, server: ServerEntity, key: KeyEntity?) {
        if (key == null) {
            throw IllegalArgumentException("未配置凭证或凭证已丢失")
        }

        val decryptedSecret = CryptoHelper.decrypt(key.encryptedSecret)
        if (decryptedSecret.isBlank()) {
            throw IllegalArgumentException("凭证数据解密后为空")
        }

        when (server.authType) {
            AuthType.PASSWORD -> {
                client.authPassword(server.username, decryptedSecret.toCharArray())
            }
            AuthType.KEY -> {
                val passphrase = if (key.encryptedPassphrase.isNotBlank()) {
                    CryptoHelper.decrypt(key.encryptedPassphrase).ifBlank { null }
                } else {
                    null
                }

                val keyProvider = createKeyProvider(client, decryptedSecret, passphrase)
                client.authPublickey(server.username, keyProvider)
            }
        }
    }

    fun createKeyProvider(
        client: SSHClient,
        privateKeyContent: String,
        passphrase: String? = null
    ): net.schmizz.sshj.userauth.keyprovider.KeyProvider {
        // 标准化私钥文本换行，防止结尾缺少换行导致 Base64 截断
        val normalizedKey = if (privateKeyContent.endsWith("\n")) {
            privateKeyContent
        } else {
            "$privateKeyContent\n"
        }

        // 使用 3 参数重载直接从内存字符串解析私钥，避免被当成磁盘文件路径导致 ENOENT (No such file or directory)
        val passwordFinder = if (!passphrase.isNullOrBlank()) {
            PasswordUtils.createOneOff(passphrase.toCharArray())
        } else {
            null
        }

        return client.loadKeys(
            normalizedKey,
            null as String?,
            passwordFinder
        )
    }

    private fun readStream(stream: InputStream?, isEOF: Boolean = true): String {
        if (stream == null) return ""
        return try {
            if (isEOF) {
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val available = stream.available()
                if (available > 0) {
                    val bytes = ByteArray(available)
                    val read = stream.read(bytes)
                    if (read > 0) String(bytes, 0, read, Charsets.UTF_8) else ""
                } else {
                    ""
                }
            }
        } catch (_: Exception) {
            ""
        }
    }
}
