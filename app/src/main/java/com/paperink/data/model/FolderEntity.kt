package com.paperink.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val colorHex: String = "#3B82F6",
    val iconName: String = "folder",
    val createdAt: Long = System.currentTimeMillis()
)
