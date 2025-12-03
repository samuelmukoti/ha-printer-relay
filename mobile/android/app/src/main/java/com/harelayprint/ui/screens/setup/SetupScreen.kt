package com.harelayprint.ui.screens.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.harelayprint.R
import com.harelayprint.ui.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTokenHelp by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome text
            Text(
                text = stringResource(R.string.setup_welcome),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // URL Field
            OutlinedTextField(
                value = uiState.haUrl,
                onValueChange = viewModel::updateUrl,
                label = { Text(stringResource(R.string.setup_url_label)) },
                placeholder = { Text(stringResource(R.string.setup_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            )

            // Token Field with visibility toggle
            OutlinedTextField(
                value = uiState.haToken,
                onValueChange = viewModel::updateToken,
                label = { Text(stringResource(R.string.setup_token_label)) },
                placeholder = { Text(stringResource(R.string.setup_token_hint)) },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showToken) "Hide token" else "Show token"
                            )
                        }
                        IconButton(onClick = { showTokenHelp = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Token help"
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            )

            // Optional token note
            Text(
                text = stringResource(R.string.setup_token_optional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Error message
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Connection status
            if (uiState.isConnected) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.setup_connection_success),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            uiState.serverVersion?.let { version ->
                                Text(
                                    text = "Server version: $version",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoading && uiState.haUrl.isNotBlank()
                ) {
                    Text(
                        if (uiState.isLoading) "Testing..."
                        else stringResource(R.string.setup_test_connection)
                    )
                }

                Button(
                    onClick = { viewModel.saveAndContinue(onSetupComplete) },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.isConnected && !uiState.isLoading
                ) {
                    Text(stringResource(R.string.setup_continue))
                }
            }
        }
    }

    // Token help dialog
    if (showTokenHelp) {
        AlertDialog(
            onDismissRequest = { showTokenHelp = false },
            title = { Text(stringResource(R.string.setup_token_help_title)) },
            text = {
                Text(stringResource(R.string.setup_token_help_body))
            },
            confirmButton = {
                TextButton(onClick = { showTokenHelp = false }) {
                    Text("OK")
                }
            }
        )
    }
}
