package com.paperink.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.paperink.data.model.DocumentEntity
import com.paperink.data.model.FolderEntity
import com.paperink.data.model.PageAnnotationEntity

@Database(
    entities = [
        FolderEntity::class,
        DocumentEntity::class,
        PageAnnotationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PaperInkDatabase : RoomDatabase() {
    abstract fun paperInkDao(): PaperInkDao

    companion object {
        @Volatile
        private var INSTANCE: PaperInkDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Version 1 to 2 schema migration
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
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
