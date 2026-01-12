package com.example.aidocumentscanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Folder entity for organizing documents.
 * System folders are auto-created and cannot be deleted.
 */
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String = "folder",  // Material icon name
    val color: Long = 0xFF6366F1,  // Default purple
    val isSystemFolder: Boolean = false,
    val documentCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * System folder types for auto-categorization
 */
enum class SystemFolder(
    val displayName: String,
    val icon: String,
    val color: Long,
    val keywords: List<String>
) {
    IDS(
        "IDs & Cards",
        "badge",
        0xFF10B981,  // Green
        listOf("id", "card", "license", "passport", "aadhar", "pan", "voter", "identity")
    ),
    NOTES(
        "Notes",
        "edit_note",
        0xFF3B82F6,  // Blue
        listOf("note", "notes", "study", "lecture", "class")
    ),
    BILLS(
        "Bills & Receipts",
        "receipt_long",
        0xFFF59E0B,  // Amber
        listOf("bill", "receipt", "invoice", "payment", "purchase")
    ),
    BOOKS(
        "Books",
        "menu_book",
        0xFF8B5CF6,  // Purple
        listOf("book", "chapter", "page", "textbook")
    ),
    FORMS(
        "Forms",
        "description",
        0xFFEC4899,  // Pink
        listOf("form", "application", "registration", "enrollment")
    ),
    UNCATEGORIZED(
        "All Documents",
        "folder",
        0xFF6B7280,  // Gray
        emptyList()
    )
}
