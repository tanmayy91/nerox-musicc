/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.music.nerox.lyrics

import android.content.Context
import android.util.LruCache
import com.music.nerox.constants.LyricsProviderOrderKey
import com.music.nerox.constants.PreferredLyricsProvider
import com.music.nerox.constants.PreferredLyricsProviderKey
import com.music.nerox.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.music.nerox.extensions.toEnum
import com.music.nerox.models.MediaMetadata
import com.music.nerox.utils.NetworkConnectivityObserver
import com.music.nerox.utils.dataStore
import com.music.nerox.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    /**
     * Resolves the ordered list of lyrics providers from the user's saved priority order.
     * Falls back to migrating the legacy [PreferredLyricsProvider] enum if the new order
     * preference has not been written yet, ensuring a smooth upgrade for existing users.
     */
    private suspend fun resolveLyricsProviders(): List<LyricsProvider> {
        val preferences = context.dataStore.data.first()
        val orderString = preferences[LyricsProviderOrderKey].orEmpty()

        if (orderString.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(orderString)
        }

        // Migration path: place the old preferred provider first in the default order
        val preferredEnum = preferences[PreferredLyricsProviderKey]
            .toEnum(PreferredLyricsProvider.YOULYPLUS)
        val preferredName = LyricsProviderRegistry.getProviderNameForEnum(preferredEnum)
        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        val migratedOrder = listOf(preferredName) + defaultOrder.filter { it != preferredName }
        return migratedOrder.mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }



    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    /**
     * v3: Parallel-race lyrics fetch — all enabled providers run simultaneously.
     * The first provider to return a non-empty result wins; all others are cancelled.
     * Per-provider timeout of [PROVIDER_TIMEOUT_MS] prevents one slow provider from
     * blocking the race.  Results are cached in an LRU of size [MAX_CACHE_SIZE].
     *
     * Previous behaviour was sequential (each provider tried one at a time), which
     * meant worst-case latency = sum of all provider timeouts.  With the race, latency
     * equals the fastest successful provider — typically 300–800 ms.
     */
    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        currentLyricsJob?.cancel()

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
        if (!isNetworkAvailable) return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")

        val providers = resolveLyricsProviders().filter { it.isEnabled(context) }
        if (providers.isEmpty()) return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")

        return withContext(Dispatchers.IO) {
            val winner    = CompletableDeferred<LyricsWithProvider>()
            val remaining = AtomicInteger(providers.size)
            val raceScope = CoroutineScope(SupervisorJob())

            providers.forEach { provider ->
                raceScope.launch {
                    try {
                        val lyrics = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                            provider.getLyrics(
                                mediaMetadata.id,
                                mediaMetadata.title,
                                mediaMetadata.artists.joinToString { it.name },
                                mediaMetadata.duration,
                                mediaMetadata.album?.title,
                            ).getOrNull()
                        }
                        if (!lyrics.isNullOrBlank() && lyrics != LYRICS_NOT_FOUND) {
                            // First valid result completes the deferred; subsequent calls are no-ops
                            winner.complete(LyricsWithProvider(lyrics, provider.name))
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    } finally {
                        // When all providers finish without a winner, complete with not-found
                        if (remaining.decrementAndGet() == 0) {
                            winner.complete(LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown"))
                        }
                    }
                }
            }

            val result = winner.await()
            raceScope.cancel() // cancel any still-running providers

            if (result.lyrics != LYRICS_NOT_FOUND) {
                cache.put(mediaMetadata.id, listOf(LyricsResult(result.provider, result.lyrics)))
            }
            result
        }
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }

        // Check network connectivity before making network requests
        // Use synchronous check as fallback if flow doesn't emit
        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            // If network check fails, try to proceed anyway
            true
        }
        
        if (!isNetworkAvailable) {
            // Still try to proceed in case of false negative
            return
        }

        val allResult = mutableListOf<LyricsResult>()
        val providers = resolveLyricsProviders()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            providers.forEach { provider ->
                if (provider.isEnabled(context)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: Exception) {
                        // Catch network-related exceptions like UnresolvedAddressException
                        reportException(e)
                    }
                }
            }
            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        /** v3: Increased from 3 → 20; covers a full listening session without re-fetching. */
        private const val MAX_CACHE_SIZE = 20
        /** v3: Each provider gets 6 s before the race moves on without it. */
        private const val PROVIDER_TIMEOUT_MS = 6_000L
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)