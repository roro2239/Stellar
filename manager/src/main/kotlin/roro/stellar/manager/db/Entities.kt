package roro.stellar.manager.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "commands")
data class CommandEntity(
    @PrimaryKey val id: String,
    val title: String,
    val command: String,
    val mode: String
)

@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey val key: String,
    val value: String
)
