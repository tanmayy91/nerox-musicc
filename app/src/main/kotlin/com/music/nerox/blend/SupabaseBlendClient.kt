package com.music.nerox.blend

import com.music.nerox.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

@Serializable
data class BlendRecord(
    val id: String = UUID.randomUUID().toString(),
    val code: String = generateCode(),
    val user1_username: String = "",
    val user2_username: String = "",
    val user1_top_artists: String = "[]",
    val user2_top_artists: String = "[]",
    val compatibility_score: Float = 0f,
    val shared_artists: String = "[]"
)

fun generateCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..6).map { chars.random() }.joinToString("")
}

object SupabaseBlendClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()
    private val jsonMedia = "application/json".toMediaType()

    private val supabaseUrl: String get() = BuildConfig.SUPABASE_URL
    private val supabaseKey: String get() = BuildConfig.SUPABASE_ANON_KEY

    val isConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabaseKey.isNotBlank()

    suspend fun saveBlend(blend: BlendRecord): Result<BlendRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(blend).toRequestBody(jsonMedia)
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/blends")
                .post(body)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "[]"
            if (!response.isSuccessful) throw Exception("Supabase error ${response.code}: $responseBody")
            val list = json.decodeFromString<List<BlendRecord>>(responseBody)
            list.firstOrNull() ?: blend
        }
    }

    suspend fun fetchBlend(code: String): Result<BlendRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/blends?code=eq.$code&limit=1")
                .get()
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "[]"
            if (!response.isSuccessful) throw Exception("Blend not found")
            val list = json.decodeFromString<List<BlendRecord>>(responseBody)
            list.firstOrNull() ?: throw Exception("No blend with code $code")
        }
    }
}
