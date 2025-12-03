package com.harelayprint.ui.screens.setup

import android.annotation.SuppressLint
import android.util.Log
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

private const val TAG = "OAuthWebView"

/**
 * WebView-based OAuth login screen.
 *
 * This screen displays the Home Assistant login page in a WebView and
 * intercepts the redirect to capture the authorization code.
 *
 * IMPORTANT: After successful login, we capture the session cookies
 * which are needed for accessing addons via the ingress proxy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OAuthWebViewScreen(
    authUrl: String,
    haBaseUrl: String,
    onAuthCodeReceived: (code: String, cookies: String?) -> Unit,
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
        },
        content = { padding ->
            OAuthWebView(
                authUrl = authUrl,
                haBaseUrl = haBaseUrl,
                onAuthCodeReceived = onAuthCodeReceived,
                onError = onError,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OAuthWebView(
    authUrl: String,
    haBaseUrl: String,
    onAuthCodeReceived: (code: String, cookies: String?) -> Unit,
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
                settings.databaseEnabled = true

                // Enable cookies - we need them for ingress access!
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                
                // Clear ALL cookies and cache to force fresh login
                cookieManager.removeAllCookies { success ->
                    Log.d(TAG, "Cleared cookies: $success")
                }
                clearCache(true)
                clearHistory()

                // Variable to store captured HA cookies
                var capturedHaCookies: String? = null
                var loginPageSeen = false

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d(TAG, "Page finished loading: $url")

                        // Capture cookies after every page load
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.flush()

                        // Try getting cookies for the loaded URL directly
                        if (url != null) {
                            val urlCookies = cookieManager.getCookie(url)
                            Log.d(TAG, "Cookies for $url: ${urlCookies?.take(300) ?: "NONE"}")

                            if (!urlCookies.isNullOrEmpty()) {
                                capturedHaCookies = urlCookies
                                Log.d(TAG, "Captured cookies from page load!")
                            }
                        }

                        // Also try the HA base URL
                        val haHost = try { java.net.URI(haBaseUrl).host } catch (e: Exception) { null }
                        if (haHost != null) {
                            val baseCookies = cookieManager.getCookie("https://$haHost")
                            Log.d(TAG, "Cookies for https://$haHost: ${baseCookies?.take(300) ?: "NONE"}")
                            if (!baseCookies.isNullOrEmpty()) {
                                capturedHaCookies = baseCookies
                            }
                        }
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        Log.d(TAG, "Page started: $url")
                        
                        // Track if we're seeing the login page
                        if (url?.contains("/auth/") == true && !url.contains("authorize?")) {
                            loginPageSeen = true
                            Log.d(TAG, "Login page detected - will capture cookies after login")
                        }
                    }

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
                                    // Use previously captured cookies (from onPageFinished)
                                    // because by the time we get here, we're redirecting to a different domain
                                    val cookies = capturedHaCookies ?: extractCookiesForDomain(haBaseUrl)
                                    Log.d(TAG, "Using cookies for ingress: ${cookies?.take(100) ?: "none"}")
                                    onAuthCodeReceived(code, cookies)
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

/**
 * Extract all cookies for a given domain from the WebView's CookieManager.
 * These cookies include the HA session cookie needed for ingress access.
 */
private fun extractCookiesForDomain(baseUrl: String): String? {
    val cookieManager = CookieManager.getInstance()

    // Flush cookies to ensure they're synced from WebView
    cookieManager.flush()

    // Get cookies for the exact URL
    var cookies = cookieManager.getCookie(baseUrl)

    // Also try without trailing slash and with different variations
    if (cookies.isNullOrEmpty()) {
        val normalizedUrl = baseUrl.trimEnd('/')
        cookies = cookieManager.getCookie(normalizedUrl)
        Log.d(TAG, "Tried normalized URL $normalizedUrl, got: ${cookies?.take(50)}")
    }

    // Try with just the domain
    if (cookies.isNullOrEmpty()) {
        try {
            val uri = java.net.URI(baseUrl)
            val domain = uri.host
            if (domain != null) {
                cookies = cookieManager.getCookie("https://$domain")
                Log.d(TAG, "Tried domain https://$domain, got: ${cookies?.take(50)}")

                if (cookies.isNullOrEmpty()) {
                    cookies = cookieManager.getCookie("http://$domain")
                    Log.d(TAG, "Tried domain http://$domain, got: ${cookies?.take(50)}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting domain from URL", e)
        }
    }

    if (cookies != null) {
        Log.d(TAG, "Found cookies for $baseUrl: ${cookies.take(100)}")
    } else {
        Log.d(TAG, "No cookies found for $baseUrl")
    }

    return cookies
}
