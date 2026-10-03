package com.antigravity.sshwake.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "commands",
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["serverId"])]
)
data class CommandEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val serverId: String,
    val command: String,
    val timeoutSeconds: Int = 8,
    val iconName: String = "ic_power",
    val colorHex: String = "#1E293B",
    val packageGroup: String = "Default", // 所属包 / 分组
    val createdAt: Long = System.currentTimeMillis()
)
