package roro.stellar.manager.db

import androidx.room.*

@Dao
interface CommandDao {
    @Query("SELECT * FROM commands")
    suspend fun getAll(): List<CommandEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(commands: List<CommandEntity>)

    @Query("DELETE FROM commands")
    suspend fun deleteAll()
}

@Dao
interface ConfigDao {
    @Query("SELECT value FROM config WHERE `key` = :key")
    fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(entity: ConfigEntity)

    @Query("SELECT * FROM config")
    fun getAll(): List<ConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setAll(entities: List<ConfigEntity>)
}
