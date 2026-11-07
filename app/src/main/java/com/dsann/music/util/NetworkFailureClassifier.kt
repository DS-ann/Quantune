package com.dsann.music.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

/**
 * Distinguishes failures caused by a missing/invalid internet connection from
 * ordinary API, parsing, authentication, and empty-result errors.
 */
object NetworkFailureClassifier {

    fun hasValidatedInternet(context: Context): Boolean {
        val connectivityManager = context.applicationContext.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as? ConnectivityManager ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    fun isNoInternetFailure(error: Throwable?, context: Context): Boolean {
        if (error == null) return false
        val internetAvailable = hasValidatedInternet(context)
        var current: Throwable? = error
        var depth = 0

        while (current != null && depth < 12) {
            when (current) {
                is UnknownHostException,
                is NoRouteToHostException -> return true
                is ConnectException -> {
                    val message = current.message.orEmpty().lowercase()
                    if (!internetAvailable || message.contains("network is unreachable") ||
                        message.contains("no route to host") ||
                        message.contains("failed to connect")
                    ) return true
                }
                is IOException -> {
                    val message = current.message.orEmpty().lowercase()
                    val clearlyNetworkRelated = listOf(
                        "unable to resolve host",
                        "no address associated with hostname",
                        "network is unreachable",
                        "no route to host",
                        "failed to connect",
                        "connection timed out",
                        "connect timed out",
                        "network not available",
                        "network is not available"
                    ).any(message::contains)
                    if (clearlyNetworkRelated || !internetAvailable) return true
                }
            }
            current = current.cause
            depth += 1
        }
        return false
    }
}
