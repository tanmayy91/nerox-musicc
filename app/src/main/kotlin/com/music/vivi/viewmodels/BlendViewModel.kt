package com.music.vivi.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.vivi.blend.BlendRecord
import com.music.vivi.blend.SupabaseBlendClient
import com.music.vivi.blend.generateCode
import com.music.vivi.db.MusicDatabase
import com.music.vivi.db.entities.Artist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

data class LocalArtistEntry(val name: String)
data class LocalTrackEntry(val name: String, val artistName: String)

data class BlendResult(
    val user1: String,
    val user2: String,
    val user1Artists: List<LocalArtistEntry>,
    val user2Artists: List<LocalArtistEntry>,
    val sharedArtists: List<String>,
    val compatibilityScore: Int,
    val blendCode: String? = null
)

@HiltViewModel
class BlendViewModel @Inject constructor(
    private val database: MusicDatabase
) : ViewModel() {

    val isLoading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val blendResult = MutableStateFlow<BlendResult?>(null)
    val savedBlendCode = MutableStateFlow<String?>(null)
    val myShareCode = MutableStateFlow<String?>(null)
    val joinedBlend = MutableStateFlow<BlendRecord?>(null)
    val isSaving = MutableStateFlow(false)
    val isJoining = MutableStateFlow(false)
    val isGeneratingCode = MutableStateFlow(false)

    private fun getYearRange(): Pair<Long, Long> {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val from = Calendar.getInstance().apply {
            set(year, Calendar.JANUARY, 1, 0, 0, 0)
        }.timeInMillis
        val to = Calendar.getInstance().apply {
            set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        }.timeInMillis
        return Pair(from, to)
    }

    private suspend fun topArtistNames(from: Long, to: Long, limit: Int = 30): List<String> {
        val raw: List<Artist> = database.mostPlayedArtists(
            fromTimeStamp = from,
            limit = limit,
            toTimeStamp = to
        ).first()
        return raw.map { entry -> entry.artist.name }
    }

    fun generateMyCode(displayName: String) {
        viewModelScope.launch {
            isGeneratingCode.value = true
            error.value = null
            val name = displayName.trim().ifBlank { "Me" }
            val (from, to) = getYearRange()
            val artistNames = topArtistNames(from, to)
            val code = generateCode()
            val record = BlendRecord(
                code = code,
                user1_username = name,
                user1_top_artists = artistNames.take(20).joinToString(",")
            )
            val saved = SupabaseBlendClient.saveBlend(record)
            myShareCode.value = saved.getOrNull()?.code ?: code
            isGeneratingCode.value = false
        }
    }

    fun createBlendFromCodes(myCode: String, friendCode: String) {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            blendResult.value = null

            val mc = myCode.trim().uppercase()
            val fc = friendCode.trim().uppercase()
            if (mc.isBlank() || fc.isBlank()) {
                error.value = "Please enter both blend codes"
                isLoading.value = false
                return@launch
            }
            val myRecordResult = SupabaseBlendClient.fetchBlend(mc)
            val friendRecordResult = SupabaseBlendClient.fetchBlend(fc)

            val myRecord = myRecordResult.getOrElse {
                error.value = "Could not find your blend code: $mc"
                isLoading.value = false
                return@launch
            }
            val friendRecord = friendRecordResult.getOrElse {
                error.value = "Could not find friend's blend code: $fc"
                isLoading.value = false
                return@launch
            }

            val u1Artists = myRecord.user1_top_artists.split(",").filter { s -> s.isNotBlank() }
            val u2Artists = friendRecord.user1_top_artists.split(",").filter { s -> s.isNotBlank() }

            val u1Set = u1Artists.map { s -> s.lowercase() }.toSet()
            val u2Set = u2Artists.map { s -> s.lowercase() }.toSet()
            val shared = u1Set.intersect(u2Set)
            val sharedNames = u1Artists.filter { s -> s.lowercase() in shared }.take(5)

            val score = computeCompatibility(u1Artists, u2Artists)

            blendResult.value = BlendResult(
                user1 = myRecord.user1_username,
                user2 = friendRecord.user1_username,
                user1Artists = u1Artists.take(10).map { s -> LocalArtistEntry(s) },
                user2Artists = u2Artists.take(10).map { s -> LocalArtistEntry(s) },
                sharedArtists = sharedNames,
                compatibilityScore = score
            )
            isLoading.value = false
        }
    }

    fun quickBlend(displayName: String) {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            blendResult.value = null

            val (from, to) = getYearRange()
            val artistNames = topArtistNames(from, to)

            if (artistNames.isEmpty()) {
                error.value = "No listening history found. Play some music first!"
                isLoading.value = false
                return@launch
            }

            val name = displayName.trim().ifBlank { "You" }
            blendResult.value = BlendResult(
                user1 = name,
                user2 = "?",
                user1Artists = artistNames.take(10).map { s -> LocalArtistEntry(s) },
                user2Artists = emptyList(),
                sharedArtists = emptyList(),
                compatibilityScore = 0
            )
            isLoading.value = false
        }
    }

    fun saveToSupabase(displayName: String) {
        viewModelScope.launch {
            isSaving.value = true
            val (from, to) = getYearRange()
            val artistNames = topArtistNames(from, to)
            val code = generateCode()
            val record = BlendRecord(
                code = code,
                user1_username = displayName.trim().ifBlank { "Me" },
                user1_top_artists = artistNames.take(20).joinToString(",")
            )
            val saved = SupabaseBlendClient.saveBlend(record)
            val finalCode = saved.getOrNull()?.code ?: code
            savedBlendCode.value = finalCode
            blendResult.value = blendResult.value?.copy(blendCode = finalCode)
            isSaving.value = false
        }
    }

    fun joinBlend(code: String) {
        viewModelScope.launch {
            isJoining.value = true
            error.value = null
            val result = SupabaseBlendClient.fetchBlend(code.trim().uppercase())
            joinedBlend.value = result.getOrElse {
                error.value = "Could not find blend with code ${code.uppercase()}"
                null
            }
            isJoining.value = false
        }
    }

    private fun computeCompatibility(u1Artists: List<String>, u2Artists: List<String>): Int {
        val u1Set = u1Artists.map { s -> s.lowercase() }.toSet()
        val u2Set = u2Artists.map { s -> s.lowercase() }.toSet()
        val overlap = u1Set.intersect(u2Set).size.toFloat()
        val union = (u1Set + u2Set).size.toFloat()
        val jaccard = if (union > 0) overlap / union else 0f

        val topN = min(u1Artists.size, min(u2Artists.size, 10))
        val u1Top = u1Artists.take(topN).map { s -> s.lowercase() }
        val u2Top = u2Artists.take(topN).map { s -> s.lowercase() }
        val topShared = u1Top.count { s -> s in u2Top }.toFloat()
        val topScore = if (topN > 0) topShared / topN else 0f

        val raw = (jaccard * 0.6f + topScore * 0.4f) * 100f
        return (raw * 1.5f).coerceIn(10f, 99f).roundToInt()
    }
}
