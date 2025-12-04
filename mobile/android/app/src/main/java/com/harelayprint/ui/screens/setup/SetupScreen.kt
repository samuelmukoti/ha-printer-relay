package com.harelayprint.ui.screens.setup

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.harelayprint.data.auth.HaAuthManager
import com.harelayprint.ui.viewmodel.SetupStep
import com.harelayprint.ui.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RelayPrint Setup") },
                navigationIcon = {
                    // Show back button during OAuth to allow canceling
                    if (uiState.setupStep == SetupStep.AUTHENTICATE) {
                        IconButton(onClick = { viewModel.cancelAuth() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (uiState.setupStep) {
            SetupStep.ENTER_URL -> {
                EnterUrlStep(
                    haUrl = uiState.haUrl,
                    onUrlChange = viewModel::updateUrl,
                    onLogin = viewModel::startLogin,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    modifier = Modifier.padding(padding)
                )
            }

            SetupStep.AUTHENTICATE -> {
                // OAuth WebView
                OAuthWebViewStep(
                    authUrl = uiState.authUrl ?: "",
                    onAuthCode = { code, cookies -> viewModel.handleAuthCode(code, cookies) },
                    onError = viewModel::handleAuthError,
                    modifier = Modifier.padding(padding)
                )
            }

            SetupStep.DISCOVERING -> {
                DiscoveringStep(
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onRetry = viewModel::retryDiscovery,
                    onReset = viewModel::resetSetup,
                    modifier = Modifier.padding(padding)
                )
            }

            SetupStep.ENTER_TUNNEL_URL -> {
                EnterTunnelUrlStep(
                    tunnelUrl = uiState.tunnelUrl ?: "",
                    onUrlChange = viewModel::updateTunnelUrl,
                    onConnect = viewModel::connectWithTunnelUrl,
                    onReset = viewModel::resetSetup,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    modifier = Modifier.padding(padding)
                )
            }

            SetupStep.COMPLETE -> {
                CompleteStep(
                    serverVersion = uiState.serverVersion,
                    tunnelUrl = uiState.tunnelUrl,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onContinue = { viewModel.continueToApp(onSetupComplete) },
                    onReset = viewModel::resetSetup,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun EnterUrlStep(
    haUrl: String,
    onUrlChange: (String) -> Unit,
    onLogin: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome text
        Text(
            text = "Enter your Home Assistant URL to connect to RelayPrint.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // URL Field
        OutlinedTextField(
            value = haUrl,
            onValueChange = onUrlChange,
            label = { Text("Home Assistant URL") },
            placeholder = { Text("https://your-ha-instance.duckdns.org") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        // Hint
        Text(
            text = "This is the URL you use to access Home Assistant in your browser.\n" +
                   "For example: https://homeassistant.local:8123 or your Nabu Casa URL.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Error message
        errorMessage?.let { error ->
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
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(),
            enabled = haUrl.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Login with Home Assistant")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OAuthWebViewStep(
    authUrl: String,
    onAuthCode: (String, String?) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    // Extract the HA base URL from the auth URL to get cookies from the right domain
    val haBaseUrl = remember(authUrl) {
        try {
            val uri = Uri.parse(authUrl)
            "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
        } catch (e: Exception) {
            null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    // Clear any previous cookies and cache for clean login
                    CookieManager.getInstance().removeAllCookies(null)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true

                            // Check if this is the OAuth callback
                            if (url != null && url.startsWith(HaAuthManager.REDIRECT_URI)) {
                                // Intercept the redirect
                                val uri = Uri.parse(url)
                                val code = uri.getQueryParameter("code")
                                val error = uri.getQueryParameter("error")
                                val errorDescription = uri.getQueryParameter("error_description")

                                if (code != null) {
                                    // Get cookies from the HA domain (not the redirect URI)
                                    val cookies = haBaseUrl?.let {
                                        CookieManager.getInstance().getCookie(it)
                                    }
                                    android.util.Log.d("OAuthWebView", "Captured cookies from $haBaseUrl: ${cookies?.take(100)}...")
                                    onAuthCode(code, cookies)
                                } else if (error != null) {
                                    onError(errorDescription ?: error)
                                }
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false

                            // Check for OAuth callback
                            if (url.startsWith(HaAuthManager.REDIRECT_URI)) {
                                val uri = Uri.parse(url)
                                val code = uri.getQueryParameter("code")
                                val error = uri.getQueryParameter("error")
                                val errorDescription = uri.getQueryParameter("error_description")

                                if (code != null) {
                                    // Get cookies from the HA domain (not the redirect URI)
                                    val cookies = haBaseUrl?.let {
                                        CookieManager.getInstance().getCookie(it)
                                    }
                                    android.util.Log.d("OAuthWebView", "Captured cookies from $haBaseUrl: ${cookies?.take(100)}...")
                                    onAuthCode(code, cookies)
                                    return true
                                } else if (error != null) {
                                    onError(errorDescription ?: error)
                                    return true
                                }
                            }

                            // Allow navigation within Home Assistant
                            return false
                        }
                    }

                    loadUrl(authUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading indicator
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun EnterTunnelUrlStep(
    tunnelUrl: String,
    onUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    onReset: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Success message
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Logged in to Home Assistant",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Explanation
        Text(
            text = "Now enter your RelayPrint tunnel URL.\n\n" +
                   "You can find this in the RelayPrint addon dashboard under Settings > Remote Access.",
            style = MaterialTheme.typography.bodyMedium
        )

        // Tunnel URL field
        OutlinedTextField(
            value = tunnelUrl,
            onValueChange = onUrlChange,
            label = { Text("Tunnel URL") },
            placeholder = { Text("https://xxx-xxx-xxx.loca.lt") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        // Hint
        Text(
            text = "The URL looks like: https://xxx-xxx-xxx.loca.lt",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Error message
        errorMessage?.let { error ->
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

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start Over")
            }

            Button(
                onClick = onConnect,
                modifier = Modifier.weight(1f),
                enabled = tunnelUrl.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Connect")
            }
        }
    }
}

@Composable
private fun DiscoveringStep(
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator()
            Text(
                text = "Discovering RelayPrint addon...",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Looking for tunnel URL in addon settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Error message
        errorMessage?.let { error ->
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

        // Retry and Reset buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start Over")
            }

            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun CompleteStep(
    serverVersion: String?,
    tunnelUrl: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onContinue: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Loading indicator for tunnel URL refresh
        if (isLoading) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Verifying connection...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Success card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Column {
                    Text(
                        text = "Connected to RelayPrint!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    serverVersion?.let { version ->
                        Text(
                            text = "Server version: $version",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Error message
        errorMessage?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Tunnel URL info
        tunnelUrl?.let { url ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Remote Access",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Text(
            text = "You're all set! RelayPrint is ready to receive print jobs from your device.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f)
            ) {
                Text("Reconfigure")
            }

            Button(
                onClick = onContinue,
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                Text("Continue")
            }
        }
    }
}
