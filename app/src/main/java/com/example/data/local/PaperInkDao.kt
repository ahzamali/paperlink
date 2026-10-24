package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DocumentEntity
import com.example.data.model.FolderEntity
import com.example.data.model.PageAnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaperInkDao {
    // Folders
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: Long)

    // Documents
    @Query("SELECT * FROM documents ORDER BY lastReadAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY lastReadAt DESC")
    fun getDocumentsByFolder(folderId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY lastReadAt DESC")
    fun getFavoriteDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents ORDER BY lastReadAt DESC LIMIT 1")
    fun getLastOpenedDocument(): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun getDocumentFlow(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE filePath = :filePath LIMIT 1")
    suspend fun getDocumentByFilePath(filePath: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET currentPage = :page, lastReadAt = :timestamp WHERE id = :id")
    suspend fun updateReadingPosition(id: Long, page: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isFavorite = :isFav WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFav: Boolean)

    @Query("UPDATE documents SET folderId = :folderId WHERE id = :id")
    suspend fun moveToFolder(id: Long, folderId: Long?)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    // Local Room Search queries by title, tag, or annotation keyword content
    @Query("""
        SELECT DISTINCT d.* FROM documents d
        LEFT JOIN page_annotations a ON d.id = a.documentId
        WHERE (:folderId IS NULL OR d.folderId = :folderId)
          AND (
              d.title LIKE '%' || :query || '%'
              OR d.tags LIKE '%' || :query || '%'
              OR (a.textContent IS NOT NULL AND a.textContent LIKE '%' || :query || '%')
          )
        ORDER BY d.lastReadAt DESC
    """)
    fun searchDocuments(query: String, folderId: Long?): Flow<List<DocumentEntity>>

    @Query("""
        SELECT DISTINCT d.* FROM documents d
        LEFT JOIN page_annotations a ON d.id = a.documentId
        WHERE d.isFavorite = 1
          AND (
              d.title LIKE '%' || :query || '%'
              OR d.tags LIKE '%' || :query || '%'
              OR (a.textContent IS NOT NULL AND a.textContent LIKE '%' || :query || '%')
          )
        ORDER BY d.lastReadAt DESC
    """)
    fun searchFavoriteDocuments(query: String): Flow<List<DocumentEntity>>

    @Query("""
        SELECT * FROM page_annotations
        WHERE textContent IS NOT NULL AND textContent LIKE '%' || :query || '%'
    """)
    fun searchMatchingAnnotations(query: String): Flow<List<PageAnnotationEntity>>

    // Annotations
    @Query("SELECT * FROM page_annotations WHERE documentId = :docId AND pageIndex = :pageIndex ORDER BY id ASC")
    fun getAnnotationsForPage(docId: Long, pageIndex: Int): Flow<List<PageAnnotationEntity>>

    @Query("SELECT * FROM page_annotations WHERE documentId = :docId ORDER BY pageIndex ASC, id ASC")
    suspend fun getAllAnnotationsForDocument(docId: Long): List<PageAnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: PageAnnotationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(annotations: List<PageAnnotationEntity>)

    @Query("DELETE FROM page_annotations WHERE documentId = :docId AND pageIndex = :pageIndex")
    suspend fun deleteAnnotationsForPage(docId: Long, pageIndex: Int)

    @Query("DELETE FROM page_annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: Long)
}
