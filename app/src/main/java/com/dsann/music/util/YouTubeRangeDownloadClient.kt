package com.dsann.music.util

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Timeout
import okio.buffer
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit

/**
 * Downloads YouTube audio without leaving a single long, open-ended googlevideo GET running.
 *
 * Some googlevideo stream responses are throttled close to real-time playback speed. The
 * range-prefetch approach translates large reads into 1 MiB ranged POST requests and prefetches the
 * next range while the current response is consumed. The original stream is downloaded
 * byte-for-byte; this does not change the selected format or bitrate.
 */
internal object YouTubeRangeDownloadClient {
    private const val TAG = "YouTubeRangeDownload"
    private const val CHUNK_SIZE = 1L shl 20
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    private val requestNumber = AtomicInteger(0)

    private val rawClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val streamClient = rawClient.newBuilder()
        .addInterceptor(YouTubeStreamInterceptor(rawClient))
        .build()

    fun newCall(streamUrl: String): Call {
        val request = Request.Builder()
            .url(streamUrl)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .build()
        return streamClient.newCall(request)
    }

    private fun isGoogleVideoStream(url: HttpUrl): Boolean =
        url.host.endsWith("googlevideo.com") && url.encodedPath.startsWith("/videoplayback")

    /** googlevideo expects ranged media requests as POSTs with a range query parameter. */
    private fun streamRequest(base: Request, from: Long, to: Long?, ranged: Boolean): Request {
        val url = base.url
        val builderUrl = url.newBuilder()
            .removeAllQueryParameters("range")
            .removeAllQueryParameters("rn")
            .setQueryParameter("rn", requestNumber.incrementAndGet().toString())
        if (ranged) builderUrl.setQueryParameter("range", "$from-" + (to?.toString() ?: ""))
        val fullUrl = url.toString()
        val builder = base.newBuilder()
            .url(builderUrl.build())
            .removeHeader("Range")
            .post(byteArrayOf(0x78, 0).toRequestBody(null))
            .header("Accept-Encoding", "identity")
            .header("User-Agent", USER_AGENT)

        if (YoutubeParsingHelper.isWebStreamingUrl(fullUrl)) {
            builder.header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "cross-site")
        }
        return builder.build()
    }


    /** Convert a rejected POST range request into a conventional GET with an HTTP Range header. */
    private fun directGetFallback(request: Request): Request {
        val rangeQuery = request.url.queryParameter("range")
        val cleanUrl = request.url.newBuilder()
            .removeAllQueryParameters("range")
            .removeAllQueryParameters("rn")
            .build()
        val builder = request.newBuilder()
            .url(cleanUrl)
            .get()
            .removeHeader("Range")
            .removeHeader("Content-Length")
            .removeHeader("Content-Type")
            .removeHeader("Transfer-Encoding")
        if (!rangeQuery.isNullOrBlank()) {
            builder.header("Range", "bytes=$rangeQuery")
        }
        return builder.build()
    }

    private fun directGetRangeRequest(base: Request, from: Long, to: Long): Request =
        directGetFallback(streamRequest(base, from, to, ranged = true))

    private fun withRetry(request: Request, send: (Request) -> Response): Response {
        val first = send(request)
        if (first.code != 403) return first
        first.close()
        try {
            Thread.sleep(350L)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while retrying a stream range", interrupted)
        }
        val retryUrl = request.url.newBuilder()
            .setQueryParameter("rn", requestNumber.incrementAndGet().toString())
            .build()
        val retry = send(request.newBuilder().url(retryUrl).build())
        if (retry.code != 403) return retry
        retry.close()

        // A signed Opus URL can reject the POST transport while accepting GET on the same URL.
        val direct = send(directGetFallback(request))
        if (direct.isSuccessful) {
            Log.i(TAG, "Direct GET fallback accepted after POST range returned 403")
        } else {
            Log.w(TAG, "Direct GET fallback was rejected: HTTP " + direct.code)
        }
        return direct
    }

    private class YouTubeStreamInterceptor(
        private val chunkClient: OkHttpClient
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url
            if (!YouTubeRangeDownloadClient.isGoogleVideoStream(url)) return chain.proceed(request)

            if (url.queryParameter("range") != null) {
                return YouTubeRangeDownloadClient.withRetry(
                    YouTubeRangeDownloadClient.streamRequest(request, 0L, null, false),
                    chain::proceed
                )
            }

            val range = request.header("Range")?.let {
                Regex("bytes=(\\d+)-(\\d*)").find(it)
            }
            val start = range?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val contentLength = url.queryParameter("clen")?.toLongOrNull()
            val end = range?.groupValues?.get(2)?.toLongOrNull()?.let { requestedEnd ->
                contentLength?.let { minOf(requestedEnd, it - 1L) } ?: requestedEnd
            } ?: contentLength?.minus(1L)

            if (contentLength != null && contentLength > 0L && end != null &&
                end - start + 1L > YouTubeRangeDownloadClient.CHUNK_SIZE
            ) {
                val firstEnd = minOf(end, start + YouTubeRangeDownloadClient.CHUNK_SIZE - 1L)
                val first = YouTubeRangeDownloadClient.withRetry(
                    YouTubeRangeDownloadClient.streamRequest(request, start, firstEnd, true),
                    chain::proceed
                )
                if (!first.isSuccessful) {
                    Log.w(TAG, "Initial stream range rejected: HTTP " + first.code)
                    return first
                }

                val length = end - start + 1L
                val body = ChunkedBody(first, start, end, request)
                val builder = first.newBuilder()
                    .body(body)
                    .removeHeader("Content-Length")
                    .removeHeader("Content-Range")
                    .header("Content-Length", length.toString())
                return if (range != null) {
                    builder.code(206)
                        .message("Partial Content")
                        .header("Content-Range", "bytes $start-$end/" + contentLength)
                        .build()
                } else {
                    builder.code(200).message("OK").build()
                }
            }

            val translatedRange = range != null &&
                !(start == 0L && range.groupValues[2].isEmpty())
            val response = YouTubeRangeDownloadClient.withRetry(
                YouTubeRangeDownloadClient.streamRequest(
                    request, start, range?.groupValues?.get(2)?.toLongOrNull(), translatedRange
                ),
                chain::proceed
            )
            if (translatedRange && response.code == 200) {
                val length = response.header("Content-Length")?.toLongOrNull()
                val builder = response.newBuilder().code(206).message("Partial Content")
                if (length != null) {
                    builder.header("Content-Range", "bytes $start-" + (start + length - 1L) + "/" + (contentLength ?: "*"))
                }
                return builder.build()
            }
            return response
        }

        private class PendingResponse(private val call: Call) {
            private val latch = CountDownLatch(1)
            private val completed = AtomicBoolean(false)
            @Volatile private var response: Response? = null
            @Volatile private var failure: IOException? = null

            fun complete(result: Response) {
                if (completed.compareAndSet(false, true)) {
                    response = result
                    latch.countDown()
                } else {
                    result.close()
                }
            }

            fun fail(error: IOException) {
                if (completed.compareAndSet(false, true)) {
                    failure = error
                    latch.countDown()
                }
            }

            fun get(): Response {
                try {
                    latch.await()
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Interrupted waiting for a stream range")
                }
                failure?.let { throw it }
                return response ?: throw IOException("Stream range returned no response")
            }

            fun cancel() {
                call.cancel()
                response?.close()
            }
        }

        /** Reads a song as one response while the following range is fetched in parallel. */
        private inner class ChunkedBody(
            first: Response,
            private val start: Long,
            private val end: Long,
            private val base: Request
        ) : ResponseBody() {
            private val contentTypeValue = first.body.contentType()
            private var position = start
            private var current: Response? = first
            private var currentEnd = minOf(end, start + YouTubeRangeDownloadClient.CHUNK_SIZE - 1L)
            private var next: Pair<Long, PendingResponse>? = null

            private fun fetch(from: Long): PendingResponse {
                val to = minOf(end, from + YouTubeRangeDownloadClient.CHUNK_SIZE - 1L)
                val request = YouTubeRangeDownloadClient.streamRequest(base, from, to, true)
                val call = chunkClient.newCall(request)
                val pending = PendingResponse(call)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        pending.fail(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.code == 403) {
                            response.close()
                            try {
                                val retry = chunkClient.newCall(
                                    YouTubeRangeDownloadClient.streamRequest(base, from, to, true)
                                ).execute()
                                if (retry.code == 403) {
                                    retry.close()
                                    val direct = chunkClient.newCall(
                                        YouTubeRangeDownloadClient.directGetRangeRequest(base, from, to)
                                    ).execute()
                                    pending.complete(direct)
                                } else {
                                    pending.complete(retry)
                                }
                            } catch (error: IOException) {
                                pending.fail(error)
                            }
                        } else {
                            pending.complete(response)
                        }
                    }
                })
                return pending
            }

            private fun prefetch() {
                val from = currentEnd + 1L
                if (from <= end && next == null) next = from to fetch(from)
            }

            private fun openNextRange() {
                current?.close()
                current = null
                val pending = next?.takeIf { it.first == position }
                if (pending == null) next?.second?.cancel()
                next = null

                val response = (pending?.second ?: fetch(position)).get()
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    throw IOException("YouTube rejected stream range at byte " + position + " (HTTP " + code + ")")
                }
                current = response
                currentEnd = minOf(end, position + YouTubeRangeDownloadClient.CHUNK_SIZE - 1L)
            }

            private val source = object : okio.Source {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (byteCount == 0L) return 0L
                    if (position > end) return -1L
                    while (true) {
                        val response = current ?: run {
                            openNextRange()
                            current ?: throw IOException("Stream range did not open")
                        }
                        if (next == null) prefetch()
                        val read = response.body.source().read(sink, minOf(byteCount, end - position + 1L))
                        if (read > 0L) {
                            position += read
                            return read
                        }
                        if (position > end) return -1L
                        current?.close()
                        current = null
                    }
                }

                override fun timeout(): Timeout = Timeout.NONE

                override fun close() {
                    current?.close()
                    current = null
                    next?.second?.cancel()
                    next = null
                }
            }.buffer()

            override fun contentType() = contentTypeValue
            override fun contentLength(): Long = end - start + 1L
            override fun source(): BufferedSource = source
        }
    }
}
