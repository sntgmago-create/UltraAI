package com.sntgmago.ultraai.shell

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AIAction(val action: String, val params: JsonObject?, val description: String)
data class ActionResult(val success: Boolean, val message: String)

class ActionExecutor(private val context: Context) {

    private val gson = Gson()

    suspend fun parseAndExecute(aiResponse: String): ActionResult? {
        val jsonMatch = Regex("""\{[^{}]*"action"[^{}]*\}""").find(aiResponse) ?: return null
        return try {
            val action = gson.fromJson(jsonMatch.value, AIAction::class.java)
            executeAction(action)
        } catch (e: Exception) { null }
    }

    suspend fun executeAction(action: AIAction): ActionResult = withContext(Dispatchers.IO) {
        when (action.action.uppercase()) {
            "OPEN_APP" -> openApp(action.params?.get("package")?.asString ?: "")
            "SHELL_COMMAND" -> executeShell(action.params?.get("command")?.asString ?: "")
            "TOGGLE_WIFI" -> toggleWifi(action.params?.get("enable")?.asBoolean ?: true)
            "TOGGLE_BLUETOOTH" -> ActionResult(false, "⚠️ Bluetooth requiere permisos adicionales en Android 12+")
            "SET_VOLUME" -> setVolume(action.params?.get("level")?.asInt ?: 50)
            "OPEN_SETTINGS" -> openSettings()
            "OPEN_URL" -> openUrl(action.params?.get("url")?.asString ?: "")
            else -> ActionResult(false, "Acción no reconocida: ${action.action}")
        }
    }

    private fun openApp(packageName: String): ActionResult {
        val commonApps = mapOf(
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "ajustes" to "com.android.settings"
        )
        val pkg = commonApps[packageName.lowercase()] ?: packageName
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ActionResult(true, "✅ App abierta: $pkg")
            } else ActionResult(false, "❌ App no encontrada: $packageName")
        } catch (e: Exception) { ActionResult(false, "❌ Error: ${e.message}") }
    }

    private fun executeShell(command: String): ActionResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            ActionResult(true, "✅ ${output.take(200)}")
        } catch (e: Exception) { ActionResult(false, "❌ Error: ${e.message}") }
    }

    private fun toggleWifi(enable: Boolean): ActionResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(Settings.Panel.ACTION_WIFI)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ActionResult(true, "📶 Abrí ajustes de WiFi")
            } else {
                @Suppress("DEPRECATION")
                val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wm.isWifiEnabled = enable
                ActionResult(true, if (enable) "📶 WiFi activado" else "📶 WiFi desactivado")
            }
        } catch (e: Exception) { ActionResult(false, "❌ Error WiFi: ${e.message}") }
    }

    private fun setVolume(level: Int): ActionResult {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, level * max / 100, 0)
            ActionResult(true, "🔊 Volumen al ${level}%")
        } catch (e: Exception) { ActionResult(false, "❌ Error volumen: ${e.message}") }
    }

    private fun openSettings(): ActionResult {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionResult(true, "⚙️ Ajustes abiertos")
        } catch (e: Exception) { ActionResult(false, "❌ Error: ${e.message}") }
    }

    private fun openUrl(url: String): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionResult(true, "🌐 Abriendo: $url")
        } catch (e: Exception) { ActionResult(false, "❌ Error: ${e.message}") }
    }
}
