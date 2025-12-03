package com.harelayprint.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.harelayprint.R
import com.harelayprint.data.api.PaperSize
import com.harelayprint.data.api.PrintQuality
import com.harelayprint.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showPaperSizeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Connection section
                SettingsSectionHeader(stringResource(R.string.settings_connection))

                SettingsItem(
                    icon = Icons.Default.Link,
                    title = stringResource(R.string.settings_server_url),
                    subtitle = uiState.haUrl.ifEmpty { "Not configured" }
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                // Print defaults section
                SettingsSectionHeader(stringResource(R.string.settings_print_defaults))

                // Default copies
                SettingsItemWithAction(
                    icon = Icons.Default.ContentCopy,
                    title = stringResource(R.string.settings_default_copies),
                    subtitle = uiState.defaultCopies.toString()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.setDefaultCopies(uiState.defaultCopies - 1) },
                            enabled = uiState.defaultCopies > 1
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease")
                        }
                        Text(
                            text = uiState.defaultCopies.toString(),
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(
                            onClick = { viewModel.setDefaultCopies(uiState.defaultCopies + 1) },
                            enabled = uiState.defaultCopies < 99
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase")
                        }
                    }
                }

                // Duplex
                SettingsItemWithSwitch(
                    icon = Icons.Default.FlipToBack,
                    title = stringResource(R.string.settings_duplex),
                    subtitle = if (uiState.defaultDuplex) "Double-sided" else "Single-sided",
                    checked = uiState.defaultDuplex,
                    onCheckedChange = viewModel::setDefaultDuplex
                )

                // Quality
                SettingsClickableItem(
                    icon = Icons.Default.HighQuality,
                    title = stringResource(R.string.settings_quality),
                    subtitle = uiState.defaultQuality.displayName,
                    onClick = { showQualityDialog = true }
                )

                // Paper size
                SettingsClickableItem(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.settings_paper_size),
                    subtitle = uiState.defaultPaperSize.displayName,
                    onClick = { showPaperSizeDialog = true }
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                // Notifications section
                SettingsSectionHeader(stringResource(R.string.settings_notifications))

                SettingsItemWithSwitch(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_notifications_enabled),
                    subtitle = if (uiState.notificationsEnabled) "Enabled" else "Disabled",
                    checked = uiState.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                // Account section
                SettingsSectionHeader(stringResource(R.string.settings_account))

                SettingsClickableItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = stringResource(R.string.settings_logout),
                    subtitle = stringResource(R.string.settings_logout_hint),
                    onClick = { showLogoutDialog = true },
                    tint = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.settings_logout_title)) },
            text = { Text(stringResource(R.string.settings_logout_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onLogout)
                    }
                ) {
                    Text(stringResource(R.string.settings_logout_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.settings_logout_cancel))
                }
            }
        )
    }

    // Quality selection dialog
    if (showQualityDialog) {
        SelectionDialog(
            title = stringResource(R.string.settings_quality),
            options = PrintQuality.entries.map { it.displayName },
            selectedIndex = PrintQuality.entries.indexOf(uiState.defaultQuality),
            onSelect = { index ->
                viewModel.setDefaultQuality(PrintQuality.entries[index])
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    // Paper size selection dialog
    if (showPaperSizeDialog) {
        SelectionDialog(
            title = stringResource(R.string.settings_paper_size),
            options = PaperSize.entries.map { it.displayName },
            selectedIndex = PaperSize.entries.indexOf(uiState.defaultPaperSize),
            onSelect = { index ->
                viewModel.setDefaultPaperSize(PaperSize.entries[index])
                showPaperSizeDialog = false
            },
            onDismiss = { showPaperSizeDialog = false }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsItemWithAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        action()
    }
}

@Composable
fun SettingsItemWithSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsClickableItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SelectionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(option)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
