package com.example.aidocumentscanner.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY isSystemFolder DESC, name ASC")
    fun getAllFolders(): Flow<List<Folder>>
    
    @Query("SELECT * FROM folders WHERE isSystemFolder = 1 ORDER BY name ASC")
    fun getSystemFolders(): Flow<List<Folder>>
    
    @Query("SELECT * FROM folders WHERE isSystemFolder = 0 ORDER BY name ASC")
    fun getCustomFolders(): Flow<List<Folder>>
    
    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): Folder?
    
    @Query("SELECT * FROM folders WHERE name = :name LIMIT 1")
    suspend fun getFolderByName(name: String): Folder?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long
    
    @Update
    suspend fun updateFolder(folder: Folder)
    
    @Delete
    suspend fun deleteFolder(folder: Folder)
    
    @Query("DELETE FROM folders WHERE id = :id AND isSystemFolder = 0")
    suspend fun deleteFolderById(id: Long)
    
    @Query("UPDATE folders SET documentCount = (SELECT COUNT(*) FROM documents WHERE folderId = :folderId) WHERE id = :folderId")
    suspend fun updateDocumentCount(folderId: Long)
    
    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getFolderCount(): Int
}
