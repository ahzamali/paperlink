package com.paperink.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page_annotations",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId"), Index(value = ["documentId", "pageIndex"])]
)
data class PageAnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val isHighlighter: Boolean,
    val colorArgb: Int,
    val strokeWidth: Float,
    val pointsData: String, // Normalized points joined by semicolons
    val textContent: String? = null,
    val textX: Float? = null,
    val textY: Float? = null,
    val createdAt: Long = System.currentTimeMillis()
)
