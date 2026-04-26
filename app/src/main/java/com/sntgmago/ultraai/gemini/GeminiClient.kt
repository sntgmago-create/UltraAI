package com.sntgmago.ultraai.gemini

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class GeminiMessage(val role: String, val parts: List<GeminiPart>)
data class GeminiPart(val text: String)
data class GeminiRequest(
    val contents: List<GeminiMessage>,
    @SerializedName("system_instruction") val systemInstruction: GeminiSystemInstruction? = null,
    @SerializedName("generationConfig") val generationConfig: GeminiConfig? = null
)
data class GeminiSystemInstruction(val parts: List<GeminiPart>)
data class GeminiConfig(val temperature: Float = 0.7f, @SerializedName("maxOutputTokens") val maxOutputTokens: Int = 2048)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)
data class GeminiCandidate(val content: GeminiMessage?)

class GeminiClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val prefs = context.getSharedPreferences("ultraai_prefs", Context.MODE_PRIVATE)
    private val conversationHistory = mutableListOf<GeminiMessage>()

    val systemPrompt = """
Eres UltraAI, un asistente de IA ultra avanzado especializado en automatización y control de dispositivos Android.
Cuando el usuario pide automatizar algo, respondé con JSON:
{"action": "TIPO_ACCION", "params": {...}, "description": "qué hace"}
Acciones disponibles: OPEN_APP, SHELL_COMMAND, TOGGLE_WIFI, TOGGLE_BLUETOOTH, SET_VOLUME, OPEN_URL, OPEN_SETTINGS
Cuando es una pregunta normal, respondé de forma natural en español argentino.
Sos creado por Sntgmago.
""".trimIndent()

    suspend fun sendMessage(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        if (apiKey.isEmpty()) return@withContext Result.failure(Exception("API Key no configurada. Ingresá tu Gemini API Key en Configuración."))

        conversationHistory.add(GeminiMessage(role = "user", parts = listOf(GeminiPart(text = userMessage))))

        val request = GeminiRequest(
            contents = conversationHistory.toList(),
            systemInstruction = GeminiSystemInstruction(parts = listOf(GeminiPart(text = systemPrompt))),
            generationConfig = GeminiConfig(temperature = 0.7f, maxOutputTokens = 2048)
        )

        val requestBody = gson.toJson(request).toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            .post(requestBody)
            .build()

        return@withContext try {
            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                conversationHistory.removeLastOrNull()
                Result.failure(Exception("Error ${response.code}: Verificá tu API Key."))
            } else {
                val aiText = gson.fromJson(responseBody, GeminiResponse::class.java)
                    .candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Sin respuesta"
                conversationHistory.add(GeminiMessage(role = "model", parts = listOf(GeminiPart(text = aiText))))
                if (conversationHistory.size > 20) { conversationHistory.removeAt(0); conversationHistory.removeAt(0) }
                Result.success(aiText)
            }
        } catch (e: IOException) {
            conversationHistory.removeLastOrNull()
            Result.failure(Exception("Sin conexión a internet."))
        } catch (e: Exception) {
            conversationHistory.removeLastOrNull()
            Result.failure(e)
        }
    }

    fun clearHistory() { conversationHistory.clear() }
    fun saveApiKey(key: String) { prefs.edit().putString("gemini_api_key", key.trim()).apply() }
    fun getApiKey(): String = prefs.getString("gemini_api_key", "") ?: ""
    fun hasApiKey(): Boolean = getApiKey().isNotEmpty()
}
