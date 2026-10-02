package com.antigravity.sshwake.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface KeyDao {
    @Query("SELECT * FROM keys ORDER BY createdAt DESC")
    fun getAllLiveData(): LiveData<List<KeyEntity>>

    @Query("SELECT * FROM keys ORDER BY createdAt DESC")
    suspend fun getAll(): List<KeyEntity>

    @Query("SELECT * FROM keys WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(key: KeyEntity)

    @Update
    suspend fun update(key: KeyEntity)

    @Delete
    suspend fun delete(key: KeyEntity)
}
