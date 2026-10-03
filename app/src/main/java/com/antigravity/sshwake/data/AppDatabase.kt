package com.antigravity.sshwake.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromKeyType(value: KeyType): String = value.name

    @TypeConverter
    fun toKeyType(value: String): KeyType = try {
        KeyType.valueOf(value)
    } catch (_: Exception) {
        KeyType.PASSWORD
    }

    @TypeConverter
    fun fromAuthType(value: AuthType): String = value.name

    @TypeConverter
    fun toAuthType(value: String): AuthType = try {
        AuthType.valueOf(value)
    } catch (_: Exception) {
        AuthType.PASSWORD
    }
}

@Database(
    entities = [KeyEntity::class, ServerEntity::class, CommandEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun keyDao(): KeyDao
    abstract fun serverDao(): ServerDao
    abstract fun commandDao(): CommandDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE keys ADD COLUMN packageGroup TEXT NOT NULL DEFAULT 'Default'")
                db.execSQL("ALTER TABLE servers ADD COLUMN packageGroup TEXT NOT NULL DEFAULT 'Default'")
                db.execSQL("ALTER TABLE commands ADD COLUMN packageGroup TEXT NOT NULL DEFAULT 'Default'")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ssh_remote_exec.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
