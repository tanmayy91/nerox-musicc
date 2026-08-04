/**
 * Nerox Music v3 — Piped API streaming fallback.
 * Used as the last-resort fallback when every YouTube client fails.
 * Tries multiple community-hosted Piped instances in sequence; the first
 * successful response wins.  Returns Pair(streamUrl, bitrateInBps) or null.
 *
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.music.nerox.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Last-resort streaming fallback via the open-source Piped API.
 *
 * Why this exists (v3):
 *  - YouTube regularly rotates cipher keys and client tokens, causing temporary
 *    "403 / stream not found" bursts even with a full client fallback chain.
 *  - Piped instances run their own server-side extraction and are unaffected by
 *    client-side YouTube token changes, giving an independent stream source.
 *  - We try 7 public instances; the first to respond under the timeout wins.
 *
 * The returned URL is a direct CDN link that ExoPlayer can play natively
 * (no HLS / DASH manifest needed — it is a progressive audio stream).
 */
object PipedStreamFallback {

    private const val TAG = "PipedStreamFallback"

    /** Hard per-instance timeout.  Fast failure keeps the UX snappy. */
    private const val INSTANCE_TIMEOUT_MS = 9_000L

    /**
     * Public Piped API instances, ordered by historical reliability.
     * Add more from https://github.com/TeamPiped/Piped/wiki/Instances
     */
    private val PIPED_INSTANCES = listOf(
        "https://piped.video",
        "https://pipedapi.kavin.rocks",
        "https://piped-api.garudalinux.org",
        "https://api.piped.projectsegfau.lt",
        "https://piped.tokhmi.xyz",
        "https://piped.moomoo.me",
        "https://watchapi.whatever.social",
    )

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .build()

    /**
     * Attempts to fetch the highest-quality audio stream URL for [videoId].
     * Returns [Pair]<streamUrl, bitrateInBps> on success, or `null` if all
     * instances fail or time out.
     *
     * This is intentionally sequential (not parallel) because:
     *  - It is already the last-resort path, called only after all YT clients fail.
     *  - Racing all instances simultaneously would spike battery / network usage
     *    for the common case (most YT clients succeed within ~2 s).
     */
    suspend fun getStreamUrl(videoId: String): Pair<String, Int>? = withContext(Dispatchers.IO) {
        for (instance in PIPED_INSTANCES) {
            Timber.tag(TAG).d("Trying Piped instance: $instance for videoId=$videoId")
            val result = withTimeoutOrNull(INSTANCE_TIMEOUT_MS) {
                runCatching { fetchFromInstance(instance, videoId) }.getOrNull()
            }
            if (result != null) {
                Timber.tag(TAG).i("Piped fallback succeeded via $instance | bitrate=${result.second}bps | videoId=$videoId")
                return@withContext result
            }
            Timber.tag(TAG).d("Piped instance $instance failed or timed out for videoId=$videoId")
        }
        Timber.tag(TAG).e("All Piped instances exhausted for videoId=$videoId")
        null
    }

    /**
     * Calls the Piped `/streams/{videoId}` endpoint and extracts the
     * highest-bitrate audio stream URL.
     */
    private fun fetchFromInstance(instance: String, videoId: String): Pair<String, Int>? {
        val request = Request.Builder()
            .url("$instance/streams/$videoId")
            .header("Accept", "application/json")
            .header("User-Agent", "NeroxMusic/3.0 (Android; piped-fallback)")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Timber.tag(TAG).d("Piped HTTP ${response.code} from $instance")
            return null
        }

        val body = response.body?.string() ?: return null
        val json = JSONObject(body)

        val audioStreams = json.optJSONArray("audioStreams") ?: run {
            Timber.tag(TAG).d("Piped: no audioStreams array from $instance")
            return null
        }

        var bestUrl: String? = null
        var bestBitrate = 0

        for (i in 0 until audioStreams.length()) {
            val stream = audioStreams.getJSONObject(i)
            val bitrate  = stream.optInt("bitrate", 0)
            val url      = stream.optString("url").takeIf { it.isNotBlank() } ?: continue

            // Prefer OPUS/webm for higher perceptual quality per bit
            val isOpus   = stream.optString("mimeType").contains("webm", ignoreCase = true) ||
                           stream.optString("codec").contains("opus", ignoreCase = true)
            val score    = bitrate + if (isOpus) 10_240 else 0

            if (score > bestBitrate) {
                bestBitrate = score
                bestUrl     = url
            }
        }

        return bestUrl?.let { it to bestBitrate.coerceAtLeast(128_000) }
    }
}
