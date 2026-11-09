package com.paperink.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cache for OCR-extracted text and bounding spans, per page.
 * Avoids re-running heavy on-device OCR on the same PDF across sessions.
 */
@Entity(
    tableName = "ocr_text_cache",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("documentId"),
        Index(value = ["documentId", "pageIndex"], unique = true)
    ]
)
data class OcrCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val fullText: String,
    val spansJson: String, // Moshi-serialized list of PositionedTextSpan
    val createdAt: Long = System.currentTimeMillis()
)
