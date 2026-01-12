package com.example.aidocumentscanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val pdfPath: String,
    val thumbnailPath: String?,
    val pageCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val size: Long = 0,
    // Phase 6: Smart Folder System
    val folderId: Long? = null,
    val documentType: String? = null,
    // Phase 3: OCR Support
    val extractedText: String? = null,
    val isOcrProcessed: Boolean = false,
    // Phase 8: History tracking
    val lastSharedAt: Long? = null,
    val lastViewedAt: Long? = null,
    // Emoji marker for important documents
    val emoji: String? = null
)
