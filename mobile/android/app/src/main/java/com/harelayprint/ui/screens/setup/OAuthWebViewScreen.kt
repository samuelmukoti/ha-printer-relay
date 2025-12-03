package com.harelayprint.ui.screens.setup

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.harelayprint.data.auth.HaAuthManager

/**
 * WebView-based OAuth login screen.
 *
 * This screen displays the Home Assistant login page in a WebView and
 * intercepts the redirect to capture the authorization code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OAuthWebViewScreen(
    authUrl: String,
    onAuthCodeReceived: (String) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Login to Home Assistant") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel"
                        )
                    }
                }
            )
        }
    ) { padding ->
        OAuthWebView(
            authUrl = authUrl,
            onAuthCodeReceived = onAuthCodeReceived,
            onError = onError,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OAuthWebView(
    authUrl: String,
    onAuthCodeReceived: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Enable JavaScript (required for HA login)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                // Clear any existing cookies/cache to ensure fresh login
                CookieManager.getInstance().removeAllCookies(null)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false

                        // Check if this is the OAuth callback
                        if (url.startsWith(HaAuthManager.REDIRECT_URI)) {
                            // Extract the authorization code
                            val code = request.url.getQueryParameter("code")
                            val error = request.url.getQueryParameter("error")
                            val errorDescription = request.url.getQueryParameter("error_description")

                            when {
                                code != null -> {
                                    onAuthCodeReceived(code)
                                }
                                error != null -> {
                                    onError(errorDescription ?: error)
                                }
                                else -> {
                                    onError("Unknown OAuth error")
                                }
                            }
                            return true // Don't load the redirect URL
                        }

                        return false // Let WebView handle other URLs
                    }
                }

                // Load the auth URL
                loadUrl(authUrl)
            }
        },
        modifier = modifier
    )
}
