package com.antigravity.sshwake

import com.antigravity.sshwake.data.AuthType
import com.antigravity.sshwake.data.CommandEntity
import com.antigravity.sshwake.data.KeyEntity
import com.antigravity.sshwake.data.KeyType
import com.antigravity.sshwake.data.ServerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityModelUnitTest {

    @Test
    fun testServerEntityDefaultsAndCopy() {
        val server1 = ServerEntity(
            name = "Prod Server",
            host = "192.168.1.100"
        )
        assertNotNull(server1.id)
        assertEquals("root", server1.username)
        assertEquals(22, server1.port)
        assertEquals(AuthType.PASSWORD, server1.authType)
        assertEquals(8, server1.timeoutSeconds)
        assertEquals("Default", server1.packageGroup)
        assertTrue(server1.createdAt > 0)

        val server2 = server1.copy(port = 2222, authType = AuthType.KEY, packageGroup = "HomeLab")
        assertEquals(2222, server2.port)
        assertEquals(AuthType.KEY, server2.authType)
        assertEquals("HomeLab", server2.packageGroup)
        assertEquals(server1.id, server2.id)

        val server3 = ServerEntity(name = "Server 3", host = "10.0.0.1")
        assertNotEquals(server1.id, server3.id)
    }

    @Test
    fun testKeyEntityCreation() {
        val key = KeyEntity(
            name = "My RSA Key",
            type = KeyType.PRIVATE_KEY,
            encryptedSecret = "ENCRYPTED_DATA_MOCK",
            encryptedPassphrase = "ENCRYPTED_PASSPHRASE_MOCK",
            packageGroup = "DevOps"
        )
        assertNotNull(key.id)
        assertEquals("My RSA Key", key.name)
        assertEquals(KeyType.PRIVATE_KEY, key.type)
        assertEquals("ENCRYPTED_DATA_MOCK", key.encryptedSecret)
        assertEquals("ENCRYPTED_PASSPHRASE_MOCK", key.encryptedPassphrase)
        assertEquals("DevOps", key.packageGroup)
    }

    @Test
    fun testCommandEntityCreation() {
        val command = CommandEntity(
            name = "Reboot NAS",
            serverId = "srv-123",
            command = "sudo reboot"
        )
        assertNotNull(command.id)
        assertEquals("Reboot NAS", command.name)
        assertEquals("srv-123", command.serverId)
        assertEquals("sudo reboot", command.command)
        assertEquals(8, command.timeoutSeconds)
        assertEquals("ic_power", command.iconName)
        assertEquals("#1E293B", command.colorHex)
        assertEquals("Default", command.packageGroup)
    }
}
