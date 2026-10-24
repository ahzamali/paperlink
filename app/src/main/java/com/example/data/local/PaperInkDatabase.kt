package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.DocumentEntity
import com.example.data.model.FolderEntity
import com.example.data.model.PageAnnotationEntity

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

        fun getDatabase(context: Context): PaperInkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PaperInkDatabase::class.java,
                    "paper_ink_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
