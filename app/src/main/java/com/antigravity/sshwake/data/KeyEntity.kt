package com.antigravity.sshwake.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class KeyType {
    PASSWORD,
    PRIVATE_KEY
}

@Entity(tableName = "keys")
data class KeyEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: KeyType,
    val encryptedSecret: String,       // 加密后的密码文本或私钥文本
    val encryptedPassphrase: String = "", // 若私钥有口令保护则存储加密口令
    val createdAt: Long = System.currentTimeMillis()
)
