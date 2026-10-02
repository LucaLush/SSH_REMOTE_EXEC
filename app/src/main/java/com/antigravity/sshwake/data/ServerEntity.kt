package com.antigravity.sshwake.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class AuthType {
    PASSWORD,
    KEY
}

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val authType: AuthType = AuthType.PASSWORD,
    val keyId: String = "",            // 关联的 KeyEntity id
    val timeoutSeconds: Int = 8,
    val createdAt: Long = System.currentTimeMillis()
)
