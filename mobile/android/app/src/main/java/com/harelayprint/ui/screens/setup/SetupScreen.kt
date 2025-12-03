package com.harelayprint.ui.screens.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.harelayprint.R
import com.harelayprint.ui.viewmodel.SetupStep
import com.harelayprint.ui.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Show WebView OAuth screen when authenticating
    if (uiState.setupStep == SetupStep.AUTHENTICATE && uiState.authUrl != null) {
        OAuthWebViewScreen(
            authUrl = uiState.authUrl!!,
            onAuthCodeReceived = { code ->
                viewModel.handleAuthCode(code)
            },
            onError = { error ->
                viewModel.handleAuthError(error)
            },
            onCancel = {
                viewModel.cancelAuth()
            }
        )
        return
    }

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

            when (uiState.setupStep) {
                SetupStep.ENTER_URL -> {
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

                    // Hint about OAuth login
                    Text(
                        text = stringResource(R.string.setup_auth_hint),
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

                    Spacer(modifier = Modifier.weight(1f))

                    // Login button
                    Button(
                        onClick = { viewModel.startLogin() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.haUrl.isNotBlank() && !uiState.isLoading
                    ) {
                        Text(stringResource(R.string.setup_login))
                    }
                }

                SetupStep.AUTHENTICATE -> {
                    // This shouldn't show since we return early for WebView
                    // But keeping as fallback
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.setup_authenticating),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                SetupStep.DISCOVERING -> {
                    // Finding addon
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator()
                        }
                        Text(
                            text = stringResource(R.string.setup_discovering),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        // Error message during discovery
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
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Retry and Reset buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::resetSetup,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.setup_reset))
                        }

                        Button(
                            onClick = viewModel::retryDiscovery,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.setup_retry_discovery))
                        }
                    }
                }

                SetupStep.COMPLETE -> {
                    // Connection status
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

                    Spacer(modifier = Modifier.weight(1f))

                    // Continue button
                    Button(
                        onClick = { viewModel.continueToApp(onSetupComplete) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.setup_continue))
                    }
                }
            }
        }
    }
}
