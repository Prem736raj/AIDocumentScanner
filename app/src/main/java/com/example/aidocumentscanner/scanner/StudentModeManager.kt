package com.example.aidocumentscanner.scanner

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

/**
 * Student Mode settings for exam/notes scanning optimization.
 * Provides preset configurations for academic document scanning.
 */
object StudentModeManager {
    
    private val Context.studentModeDataStore: DataStore<Preferences> by preferencesDataStore(name = "student_mode")
    
    private val ENABLED = booleanPreferencesKey("student_mode_enabled")
    private val SHARP_TEXT = booleanPreferencesKey("sharp_text_enhancement")
    private val MARGIN_CLEANUP = booleanPreferencesKey("margin_cleanup")
    private val PAGE_NUMBERING = booleanPreferencesKey("page_numbering")
    private val BLACK_WHITE = booleanPreferencesKey("black_and_white")
    private val AUTO_FILENAME = booleanPreferencesKey("auto_filename")
    private val SUBJECT = stringPreferencesKey("subject")
    
    data class StudentModeSettings(
        val enabled: Boolean = false,
        val sharpTextEnhancement: Boolean = true,
        val marginCleanup: Boolean = true,
        val pageNumbering: Boolean = false,
        val blackAndWhite: Boolean = false,
        val autoFilename: Boolean = true,
        val subject: String = ""
    )
    
    /**
     * Get student mode settings flow
     */
    fun getSettings(context: Context): Flow<StudentModeSettings> {
        return context.studentModeDataStore.data.map { prefs ->
            StudentModeSettings(
                enabled = prefs[ENABLED] ?: false,
                sharpTextEnhancement = prefs[SHARP_TEXT] ?: true,
                marginCleanup = prefs[MARGIN_CLEANUP] ?: true,
                pageNumbering = prefs[PAGE_NUMBERING] ?: false,
                blackAndWhite = prefs[BLACK_WHITE] ?: false,
                autoFilename = prefs[AUTO_FILENAME] ?: true,
                subject = prefs[SUBJECT] ?: ""
            )
        }
    }
    
    /**
     * Get current settings synchronously
     */
    fun getSettingsSync(context: Context): StudentModeSettings {
        return runBlocking {
            getSettings(context).first()
        }
    }
    
    /**
     * Update student mode enabled state
     */
    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.studentModeDataStore.edit { prefs ->
            prefs[ENABLED] = enabled
        }
    }
    
    /**
     * Update all settings
     */
    suspend fun updateSettings(context: Context, settings: StudentModeSettings) {
        context.studentModeDataStore.edit { prefs ->
            prefs[ENABLED] = settings.enabled
            prefs[SHARP_TEXT] = settings.sharpTextEnhancement
            prefs[MARGIN_CLEANUP] = settings.marginCleanup
            prefs[PAGE_NUMBERING] = settings.pageNumbering
            prefs[BLACK_WHITE] = settings.blackAndWhite
            prefs[AUTO_FILENAME] = settings.autoFilename
            prefs[SUBJECT] = settings.subject
        }
    }
    
    /**
     * Generate auto filename based on settings
     */
    fun generateFilename(settings: StudentModeSettings): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return if (settings.subject.isNotBlank()) {
            "${settings.subject}_$date"
        } else {
            "Scan_$date"
        }
    }
    
    /**
     * Apply student mode enhancements to bitmap
     */
    fun applyEnhancements(bitmap: Bitmap, settings: StudentModeSettings): Bitmap {
        var result = bitmap
        
        if (settings.sharpTextEnhancement) {
            result = ImageEnhancer.applyFilter(result, ImageEnhancer.FilterType.SHARPEN)
            result = ImageEnhancer.adjustContrast(result, 1.3f)
        }
        
        if (settings.blackAndWhite) {
            result = ImageEnhancer.applyFilter(result, ImageEnhancer.FilterType.BLACK_WHITE)
        } else if (settings.marginCleanup) {
            result = ImageEnhancer.adjustBrightness(result, 20)
        }
        
        return result
    }
    
    /**
     * Get enhancement settings for student mode
     */
    fun getEnhancementSettings(settings: StudentModeSettings): DocumentTypeDetector.EnhancementSettings {
        return DocumentTypeDetector.EnhancementSettings(
            contrast = if (settings.sharpTextEnhancement) 1.4f else 1.0f,
            brightness = if (settings.marginCleanup) 20 else 0,
            sharpness = if (settings.sharpTextEnhancement) 1.5f else 1.0f,
            denoise = true,
            removeBackground = settings.marginCleanup,
            blackAndWhite = settings.blackAndWhite
        )
    }
}

/**
 * Subject presets for quick selection
 */
object SubjectPresets {
    val commonSubjects = listOf(
        "Mathematics",
        "Physics",
        "Chemistry",
        "Biology",
        "English",
        "History",
        "Geography",
        "Economics",
        "Computer Science",
        "Hindi",
        "Sanskrit",
        "Social Science",
        "Accounts",
        "Business Studies",
        "Political Science",
        "Psychology",
        "Sociology",
        "Notes",
        "Assignment",
        "Question Paper",
        "Answer Sheet"
    )
}
