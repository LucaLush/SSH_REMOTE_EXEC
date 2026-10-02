package com.antigravity.sshwake.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY createdAt DESC")
    fun getAllLiveData(): LiveData<List<ServerEntity>>

    @Query("SELECT * FROM servers ORDER BY createdAt DESC")
    suspend fun getAll(): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: ServerEntity)

    @Update
    suspend fun update(server: ServerEntity)

    @Delete
    suspend fun delete(server: ServerEntity)
}
