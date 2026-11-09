package com.paperink.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.paperink.data.model.DocumentEntity
import com.paperink.data.model.FolderEntity
import com.paperink.data.model.OcrCacheEntity
import com.paperink.data.model.PageAnnotationEntity

@Database(
    entities = [
        FolderEntity::class,
        DocumentEntity::class,
        PageAnnotationEntity::class,
        OcrCacheEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PaperInkDatabase : RoomDatabase() {
    abstract fun paperInkDao(): PaperInkDao

    companion object {
        @Volatile
        private var INSTANCE: PaperInkDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to documents
                db.execSQL("ALTER TABLE documents ADD COLUMN isImageBased INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documents ADD COLUMN ocrStatus TEXT NOT NULL DEFAULT 'NONE'")

                // Create ocr_text_cache table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ocr_text_cache (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        documentId INTEGER NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        fullText TEXT NOT NULL,
                        spansJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (documentId) REFERENCES documents(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Create indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ocr_text_cache_documentId ON ocr_text_cache(documentId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ocr_text_cache_documentId_pageIndex ON ocr_text_cache(documentId, pageIndex)")
            }
        }

        fun getDatabase(context: Context): PaperInkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PaperInkDatabase::class.java,
                    "paper_ink_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun getInstance(context: Context): PaperInkDatabase = getDatabase(context)
    }
}
