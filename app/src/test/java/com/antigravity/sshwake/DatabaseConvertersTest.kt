package com.antigravity.sshwake

import com.antigravity.sshwake.data.AuthType
import com.antigravity.sshwake.data.Converters
import com.antigravity.sshwake.data.KeyType
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseConvertersTest {

    private val converters = Converters()

    @Test
    fun testKeyTypeConversion() {
        assertEquals("PASSWORD", converters.fromKeyType(KeyType.PASSWORD))
        assertEquals("PRIVATE_KEY", converters.fromKeyType(KeyType.PRIVATE_KEY))

        assertEquals(KeyType.PASSWORD, converters.toKeyType("PASSWORD"))
        assertEquals(KeyType.PRIVATE_KEY, converters.toKeyType("PRIVATE_KEY"))

        // 测试异常值容错回退
        assertEquals(KeyType.PASSWORD, converters.toKeyType("INVALID_UNKNOWN"))
    }

    @Test
    fun testAuthTypeConversion() {
        assertEquals("PASSWORD", converters.fromAuthType(AuthType.PASSWORD))
        assertEquals("KEY", converters.fromAuthType(AuthType.KEY))

        assertEquals(AuthType.PASSWORD, converters.toAuthType("PASSWORD"))
        assertEquals(AuthType.KEY, converters.toAuthType("KEY"))

        // 测试异常值容错回退
        assertEquals(AuthType.PASSWORD, converters.toAuthType("CORRUPTED_VALUE"))
    }
}
