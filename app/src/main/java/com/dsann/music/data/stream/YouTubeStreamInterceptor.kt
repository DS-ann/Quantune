package com.dsann.music.data.stream

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adapts Media3 byte-range reads to the request shape accepted by YouTube's googlevideo CDN.
 * Ordinary web URLs and local media are never changed by this interceptor.
 */
class YouTubeStreamInterceptor : Interceptor {
    private val requestNumber = AtomicInteger(0)

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url
        if (!isGoogleVideoStream(url)) return chain.proceed(original)

        val match = original.header("Range")?.let {
            Regex("bytes=(\\d+)-(\\d*)").find(it)
        }
        val start = match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val rawEnd = match?.groupValues?.get(2).orEmpty()
        val totalLength = url.queryParameter("clen")?.toLongOrNull()
        val end = rawEnd.toLongOrNull()?.let { requested ->
            totalLength?.let { minOf(requested, it - 1L) } ?: requested
        }
        val finiteRange = match != null && !(start == 0L && rawEnd.isEmpty())

        fun buildTranslatedRequest(number: Int): Request {
            val newUrl = url.newBuilder().setQueryParameter("rn", number.toString())
            if (finiteRange) newUrl.setQueryParameter("range", "$start-${end ?: ""}")
            return original.newBuilder()
                .url(newUrl.build())
                .removeHeader("Range")
                .post(byteArrayOf(0x78, 0).toRequestBody(null))
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Accept-Encoding", "identity")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "cross-site")
                .build()
        }

        val first = chain.proceed(buildTranslatedRequest(requestNumber.incrementAndGet()))
        val response = if (first.code == 403) {
            first.close()
            // googlevideo sometimes rejects a single request number; retry once with a fresh one.
            chain.proceed(buildTranslatedRequest(requestNumber.incrementAndGet()))
        } else {
            first
        }

        if (finiteRange && response.code == 200) {
            val responseLength = response.header("Content-Length")?.toLongOrNull()
                ?: response.body.contentLength().takeIf { it >= 0L }
            val builder = response.newBuilder().code(206).message("Partial Content")
            if (responseLength != null && responseLength > 0L) {
                val responseEnd = start + responseLength - 1L
                builder.header(
                    "Content-Range",
                    "bytes $start-$responseEnd/${totalLength?.toString() ?: "*"}"
                )
            }
            return builder.build()
        }
        return response
    }

    private fun isGoogleVideoStream(url: HttpUrl): Boolean =
        url.host.endsWith("googlevideo.com", ignoreCase = true) &&
            url.encodedPath.startsWith("/videoplayback")

    private companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
