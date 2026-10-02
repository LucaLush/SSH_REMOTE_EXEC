package com.antigravity.sshwake.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update

data class CommandWithServer(
    @Embedded
    val command: CommandEntity,
    @Relation(
        parentColumn = "serverId",
        entityColumn = "id"
    )
    val server: ServerEntity?
)

@Dao
interface CommandDao {
    @Transaction
    @Query("SELECT * FROM commands ORDER BY createdAt DESC")
    fun getAllWithServerLiveData(): LiveData<List<CommandWithServer>>

    @Transaction
    @Query("SELECT * FROM commands ORDER BY createdAt DESC")
    suspend fun getAllWithServer(): List<CommandWithServer>

    @Transaction
    @Query("SELECT * FROM commands WHERE id = :id LIMIT 1")
    suspend fun getWithServerById(id: String): CommandWithServer?

    @Query("SELECT * FROM commands WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CommandEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(command: CommandEntity)

    @Update
    suspend fun update(command: CommandEntity)

    @Delete
    suspend fun delete(command: CommandEntity)
}
