package com.dsann.music.ui.explore

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.dsann.music.ui.theme.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.dsann.music.data.innertube.YouTubeMusic
import com.dsann.music.viewmodel.AccountViewModel
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")

@Composable
fun GoogleSignInScreen(
    accountViewModel: AccountViewModel,
    onSignInSuccess: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    isOnboarding: Boolean = false,
    requireExplicitContinue: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var noInternetError by remember { mutableStateOf(false) }
    var signInWebView by remember { mutableStateOf<WebView?>(null) }
    var pendingLoginCookies by remember { mutableStateOf<String?>(null) }

    val completeSignIn: (String) -> Unit = { cookies ->
        if (!isLoading) {
            isLoading = true
            pendingLoginCookies = null
            scope.launch {
                accountViewModel.onLoggedIn(cookies)
                val result = YouTubeMusic.accountProfile()
                val username = result.getOrNull()?.name ?: "Connected User"
                if (!isOnboarding) {
                    com.dsann.music.ui.SnackbarController.showMessage("Login Successful: $username")
                }
                onSignInSuccess()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign In with Google", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(DsmusicTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            signInWebView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    noInternetError = false
                                    if (android.net.Uri.parse(url.orEmpty()).host != "music.youtube.com") {
                                        pendingLoginCookies = null
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true && error != null &&
                                        (error.errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
                                            error.errorCode == WebViewClient.ERROR_CONNECT ||
                                            error.errorCode == WebViewClient.ERROR_TIMEOUT)
                                    ) {
                                        noInternetError = true
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)

                                    // Do not accept cookies while still on Google sign-in pages.
                                    // The initial Google page can see old WebView cookies and used
                                    // to close the sign-in screen before the user could log in.
                                    val pageHost = runCatching {
                                        android.net.Uri.parse(url.orEmpty()).host
                                    }.getOrNull()
                                    if (!pageHost.equals("music.youtube.com", ignoreCase = true)) return

                                    val cookies = CookieManager.getInstance()
                                        .getCookie("https://music.youtube.com")
                                    if (cookies != null &&
                                        cookies.contains("SAPISID") &&
                                        cookies.contains("HSID") &&
                                        !isLoading
                                    ) {
                                        if (requireExplicitContinue) {
                                            pendingLoginCookies = cookies
                                        } else {
                                            completeSignIn(cookies)
                                        }
                                    }
                                }
                            }
                            loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https://music.youtube.com/")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                when {
                    noInternetError -> {
                        com.dsann.music.ui.theme.NoInternetState(
                            modifier = Modifier.fillMaxSize(),
                            onRetry = {
                                noInternetError = false
                                signInWebView?.reload()
                            },
                            message = "The sign-in page couldn't be reached. Check your connection and retry."
                        )
                    }
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            com.dsann.music.ui.theme.LoadingIndicator()
                        }
                    }
                }
            }

            if (requireExplicitContinue) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DsmusicTheme.colorScheme.surface)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (pendingLoginCookies != null) {
                            "YouTube Music is ready. Continue to connect this account to the app."
                        } else {
                            "Complete Google sign-in above. Once YouTube Music opens, continue to connect your account."
                        },
                        style = DsmusicTheme.typography.bodySmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                    if (pendingLoginCookies != null) {
                        DsmusicButton(
                            onClick = { pendingLoginCookies?.let(completeSignIn) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Text("Continue")
                        }
                    }
                }
            }
        }
    }
}
