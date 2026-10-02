package com.antigravity.sshwake

import com.antigravity.sshwake.ssh.SSHExecutor
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.Security

class SSHExecutorUnitTest {

    companion object {
        // 标准未加密 OpenSSH Ed25519 私钥测试样本
        private const val TEST_ED25519_KEY =
            "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
            "b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW\n" +
            "QyNTUxOQAAACBQstpkHohZ/LG21v76d3ux9t0GrgUfmlF8TG05HQXdbwAAAJhyG+TIchvk\n" +
            "yAAAAAtzc2gtZWQyNTUxOQAAACBQstpkHohZ/LG21v76d3ux9t0GrgUfmlF8TG05HQXdbw\n" +
            "AAAEDS8jQUU/aHWPftoelEnqdspUmkUu+u1BeHDRl4DYmMe1Cy2mQeiFn8sbbW/vp3e7H2\n" +
            "3QauBR+aUXxMbTkdBd1vAAAAEHRlc3RAYW50aWdyYXZpdHkBAgMEBQ==\n" +
            "-----END OPENSSH PRIVATE KEY-----\n"

        // 密码加密的 OpenSSH Ed25519 私钥测试样本 (密码: secret123)
        private const val TEST_ED25519_KEY_ENCRYPTED =
            "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
            "b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAAGAAAABBono5ipt\n" +
            "YDFxLIFKsG+GA+AAAAEAAAAAEAAAAzAAAAC3NzaC1lZDI1NTE5AAAAIPuHOAYnGgvaE8d0\n" +
            "a+VMMsJJbv5AxoofT1NzKQPvHojCAAAAoIqR7c9EXxZ2RNKczB0yCF3ZnLwO/zyuJ7mVJ+\n" +
            "nrjdDxYZ9xuoq6YEgXSsgXUv+hC4ocN68944i+s0FQsjwOQq4Iz6MGuzRmADmTkUtdZjS9\n" +
            "2SPbDLVdz/mub+JYKJE3LVEjAAS1IJYh/fNImRU5Pz2qf/MGSqdUIOhPf5pqSKY6aYcKKo\n" +
            "idm+ioZjtuRrlmu3ZrcSzqoVKJDsdDntR/Hro=\n" +
            "-----END OPENSSH PRIVATE KEY-----\n"
    }

    private lateinit var client: SSHClient

    @Before
    fun setUp() {
        // 保证 BouncyCastleProvider 正确初始化
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
        client = SSHClient()
    }

    @Test
    fun testBouncyCastleProviderIsRegistered() {
        val provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        assertNotNull("BouncyCastle 必须已注册，否则 Ed25519 / X25519 会报错", provider)
    }

    @Test
    fun testLoadPlainEd25519KeyInMemory() {
        val keyProvider: KeyProvider = SSHExecutor.createKeyProvider(
            client = client,
            privateKeyContent = TEST_ED25519_KEY,
            passphrase = null
        )
        assertNotNull("应该成功解析纯文本内存私钥", keyProvider)
        assertNotNull("KeyProvider 应该包含有效的 PrivateKey 对象", keyProvider.getPrivate())
    }

    @Test
    fun testLoadKeyWithoutTrailingNewlineAutoNormalized() {
        // 测试结尾被不小心去掉换行符的情况
        val keyWithoutNewline = TEST_ED25519_KEY.trimEnd('\n', '\r')
        val keyProvider = SSHExecutor.createKeyProvider(
            client = client,
            privateKeyContent = keyWithoutNewline,
            passphrase = null
        )
        assertNotNull("结尾缺少换行符时，自动补全换行后应正常解析", keyProvider)
    }

    @Test
    fun testLoadEncryptedKeyWithCorrectPassphrase() {
        val keyProvider = SSHExecutor.createKeyProvider(
            client = client,
            privateKeyContent = TEST_ED25519_KEY_ENCRYPTED,
            passphrase = "secret123"
        )
        assertNotNull("输入正确口令后应该成功解密并解析私钥", keyProvider)
        assertNotNull("解密后的 KeyProvider 应该包含有效的 PrivateKey 对象", keyProvider.getPrivate())
    }

    @Test(expected = Exception::class)
    fun testLoadEncryptedKeyWithWrongPassphraseFails() {
        val provider = SSHExecutor.createKeyProvider(
            client = client,
            privateKeyContent = TEST_ED25519_KEY_ENCRYPTED,
            passphrase = "wrong_password_xyz"
        )
        // 关键：SSHJ 的 FileKeyProvider 是延迟读取的，必须调用 getPrivate() 才会真正触发解密
        provider.getPrivate()
    }

    @Test
    fun testPassphraseNullOrBlankDoesNotThrowNPE() {
        // 验证不会因 passphrase 为 null 或空白抛出 toCharArray() NPE
        val keyProvider = SSHExecutor.createKeyProvider(
            client = client,
            privateKeyContent = TEST_ED25519_KEY,
            passphrase = ""
        )
        assertNotNull("未加密密钥即使传入空口令也应正常读取", keyProvider.getPrivate())
    }

    @Test(expected = Exception::class)
    fun testMalformedKeyThrowsException() {
        val provider = SSHExecutor.createKeyProvider(
            client = client,
            privateKeyContent = "NOT_A_VALID_KEY_CONTENT",
            passphrase = null
        )
        provider.getPrivate()
    }
}
