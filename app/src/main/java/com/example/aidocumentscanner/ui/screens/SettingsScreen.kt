package com.example.aidocumentscanner.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.ui.theme.ThemeMode

/**
 * Storage location options for saving PDFs
 */
enum class StorageLocation(val label: String, val description: String, val icon: ImageVector) {
    INTERNAL("App Storage", "Private app folder (not visible in file manager)", Icons.Default.PhoneAndroid),
    DOCUMENTS("Documents Folder", "Public Documents/AIDocumentScanner folder", Icons.Default.Folder),
    DOWNLOADS("Downloads Folder", "Public Downloads folder", Icons.Default.Download)
}

object SettingsPreferences {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_STORAGE_LOCATION = "storage_location"
    private const val KEY_DEFAULT_PAGE_SIZE = "default_page_size"
    private const val KEY_DEFAULT_QUALITY = "default_quality"
    private const val KEY_AUTO_DETECT = "auto_detect"
    
    fun getStorageLocation(context: Context): StorageLocation {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_STORAGE_LOCATION, StorageLocation.DOCUMENTS.name)
        return try {
            StorageLocation.valueOf(value ?: StorageLocation.DOCUMENTS.name)
        } catch (e: Exception) {
            StorageLocation.DOCUMENTS
        }
    }
    
    fun setStorageLocation(context: Context, location: StorageLocation) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_STORAGE_LOCATION, location.name).apply()
    }
    
    fun getDefaultPageSize(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEFAULT_PAGE_SIZE, "A4") ?: "A4"
    }
    
    fun setDefaultPageSize(context: Context, size: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_PAGE_SIZE, size).apply()
    }
    
    fun getDefaultQuality(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEFAULT_QUALITY, "High") ?: "High"
    }
    
    fun setDefaultQuality(context: Context, quality: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_QUALITY, quality).apply()
    }
    
    fun getAutoDetect(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_DETECT, true)
    }
    
    fun setAutoDetect(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_DETECT, enabled).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var defaultPageSize by remember { mutableStateOf(SettingsPreferences.getDefaultPageSize(context)) }
    var defaultQuality by remember { mutableStateOf(SettingsPreferences.getDefaultQuality(context)) }
    var autoDetect by remember { mutableStateOf(SettingsPreferences.getAutoDetect(context)) }
    var storageLocation by remember { mutableStateOf(SettingsPreferences.getStorageLocation(context)) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Settings", fontWeight = FontWeight.Bold) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Storage Settings Section
            SettingsSection(title = "Storage") {
                SettingsItem(
                    title = "Save Location",
                    subtitle = storageLocation.label,
                    icon = storageLocation.icon,
                    onClick = { showStorageDialog = true }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // PDF Settings Section
            SettingsSection(title = "PDF Settings") {
                SettingsDropdown(
                    title = "Default Page Size",
                    subtitle = defaultPageSize,
                    options = listOf("A4", "Letter", "Legal", "Fit to Image"),
                    onSelect = { 
                        defaultPageSize = it
                        SettingsPreferences.setDefaultPageSize(context, it)
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                SettingsDropdown(
                    title = "Image Quality",
                    subtitle = defaultQuality,
                    options = listOf("Standard", "High", "Ultra"),
                    onSelect = { 
                        defaultQuality = it
                        SettingsPreferences.setDefaultQuality(context, it)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Scanning Settings Section
            SettingsSection(title = "Scanning") {
                SettingsSwitch(
                    title = "Auto Edge Detection",
                    subtitle = "Automatically detect document edges",
                    checked = autoDetect,
                    onCheckedChange = { 
                        autoDetect = it
                        SettingsPreferences.setAutoDetect(context, it)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Appearance Section
            SettingsSection(title = "Appearance") {
                SettingsItem(
                    title = "Theme",
                    subtitle = when (currentThemeMode) {
                        ThemeMode.SYSTEM -> "System Default"
                        ThemeMode.LIGHT -> "Light Mode"
                        ThemeMode.DARK -> "Dark Mode"
                    },
                    icon = when (currentThemeMode) {
                        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                        ThemeMode.LIGHT -> Icons.Default.LightMode
                        ThemeMode.DARK -> Icons.Default.DarkMode
                    },
                    onClick = { showThemeDialog = true }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // About Section
            SettingsSection(title = "About") {
                SettingsItem(
                    title = "Version",
                    subtitle = "1.0.0",
                    icon = Icons.Default.Info,
                    onClick = { }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                SettingsItem(
                    title = "Offline Mode",
                    subtitle = "All features work without internet",
                    icon = Icons.Default.WifiOff,
                    onClick = { }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Storage location dialog
    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
            title = { Text("Save Location", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    StorageLocation.entries.forEach { location ->
                        Card(
                            onClick = {
                                storageLocation = location
                                SettingsPreferences.setStorageLocation(context, location)
                                showStorageDialog = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (storageLocation == location)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    location.icon,
                                    contentDescription = null,
                                    tint = if (storageLocation == location)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        location.label,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        location.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (storageLocation == location) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Theme selection dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme") },
            text = {
                Column {
                    ThemeOption(
                        icon = Icons.Default.BrightnessAuto,
                        title = "System Default",
                        subtitle = "Follow system theme settings",
                        selected = currentThemeMode == ThemeMode.SYSTEM,
                        onClick = {
                            onThemeModeChange(ThemeMode.SYSTEM)
                            showThemeDialog = false
                        }
                    )
                    ThemeOption(
                        icon = Icons.Default.LightMode,
                        title = "Light Mode",
                        subtitle = "Always use light theme",
                        selected = currentThemeMode == ThemeMode.LIGHT,
                        onClick = {
                            onThemeModeChange(ThemeMode.LIGHT)
                            showThemeDialog = false
                        }
                    )
                    ThemeOption(
                        icon = Icons.Default.DarkMode,
                        title = "Dark Mode",
                        subtitle = "Always use dark theme",
                        selected = currentThemeMode == ThemeMode.DARK,
                        onClick = {
                            onThemeModeChange(ThemeMode.DARK)
                            showThemeDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ThemeOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = null)
        },
        trailingContent = {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    title: String,
    subtitle: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(subtitle) },
            trailingContent = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    trailingIcon = {
                        if (option == subtitle) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    }
}
