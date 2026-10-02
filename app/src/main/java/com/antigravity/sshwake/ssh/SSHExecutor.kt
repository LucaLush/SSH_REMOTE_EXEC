package com.antigravity.sshwake.ssh

import com.antigravity.sshwake.data.AuthType
import com.antigravity.sshwake.data.KeyEntity
import com.antigravity.sshwake.data.KeyType
import com.antigravity.sshwake.data.ServerEntity
import com.antigravity.sshwake.security.CryptoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
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
            client.connectTimeout = (timeoutSeconds * 1000).coerceAtLeast(3000)
            client.timeout = (timeoutSeconds * 1000).coerceAtLeast(3000)

            // 连接目标主机
            client.connect(server.host, server.port)

            // 认证鉴权
            authenticate(client, server, key)

            val session = client.startSession()
            try {
                val cmd = session.exec(command)
                cmd.join(timeoutSeconds.toLong(), TimeUnit.SECONDS)

                val stdout = readStream(cmd.inputStream)
                val stderr = readStream(cmd.errorStream)
                val exitStatus = cmd.exitStatus

                val fullOutput = buildString {
                    if (stdout.isNotBlank()) append(stdout)
                    if (stderr.isNotBlank()) {
                        if (isNotEmpty()) append("\n[STDERR]\n")
                        append(stderr)
                    }
                }.trim()

                val isSuccess = (exitStatus == null || exitStatus == 0)
                SSHResult(
                    isSuccess = isSuccess,
                    exitCode = exitStatus,
                    output = fullOutput.ifBlank { "执行成功（命令无回传输出）" },
                    errorMessage = if (!isSuccess) "进程退出代码: $exitStatus" else null
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

                // 标准化私钥文本换行，防止结尾缺少换行导致 Base64 截断
                val normalizedKey = if (decryptedSecret.endsWith("\n")) {
                    decryptedSecret
                } else {
                    "$decryptedSecret\n"
                }

                // 核心修复：使用 3 参数重载直接从内存字符串解析私钥，避免被当成磁盘文件路径导致 ENOENT (No such file or directory)
                val passwordFinder = if (!passphrase.isNullOrBlank()) {
                    net.schmizz.sshj.common.PasswordUtils.createOneOff(passphrase.toCharArray())
                } else {
                    null
                }

                val keyProvider = client.loadKeys(
                    normalizedKey,
                    null,
                    passwordFinder
                )
                client.authPublickey(server.username, keyProvider)
            }
        }
    }

    private fun readStream(stream: InputStream): String {
        return try {
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }
}
