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
    isOnboarding: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var noInternetError by remember { mutableStateOf(false) }
    var signInWebView by remember { mutableStateOf<WebView?>(null) }

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
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(DsmusicTheme.colorScheme.background)
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
                                val cookieManager = CookieManager.getInstance()
                                val cookies = cookieManager.getCookie("https://music.youtube.com")
                                if (cookies != null && cookies.contains("SAPISID") && cookies.contains("HSID") && !isLoading) {
                                    isLoading = true
                                    scope.launch {
                                        // Save credentials and set cookie
                                        accountViewModel.onLoggedIn(cookies)
                                        
                                        // Fetch user profile to get username
                                        val result = YouTubeMusic.accountProfile()
                                        val username = result.getOrNull()?.name ?: "Connected User"
                                        
                                        if (!isOnboarding) {
                                            com.dsann.music.ui.SnackbarController.showMessage("Login Successful: $username")
                                        }
                                        onSignInSuccess()
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
    }
}
