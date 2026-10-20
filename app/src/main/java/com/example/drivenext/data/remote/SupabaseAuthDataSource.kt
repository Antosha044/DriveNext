package com.example.drivenext.data.remote

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AuthResult(val accessToken: String?, val userId: String?)

class SupabaseAuthDataSource(private val projectUrl: String, private val anonKey: String) {
    fun isConfigured() = projectUrl.startsWith("https://") && anonKey.isNotBlank()
    fun googleOAuthUrl(): String? = if (isConfigured()) projectUrl.trimEnd('/') +
        "/auth/v1/authorize?provider=google&redirect_to=" + android.net.Uri.encode("drivenext://auth-callback") else null

    fun signIn(email: String, password: String): AuthResult = request("/auth/v1/token?grant_type=password", JSONObject()
        .put("email", email.trim()).put("password", password))

    fun signUp(email: String, password: String, metadata: JSONObject): AuthResult = request("/auth/v1/signup", JSONObject()
        .put("email", email.trim()).put("password", password).put("data", metadata))

    fun updateUserMetadata(accessToken: String, metadata: JSONObject) {
        val connection = (URL(projectUrl.trimEnd('/') + "/auth/v1/user").openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"; connectTimeout = 12_000; readTimeout = 12_000; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("apikey", anonKey); setRequestProperty("Authorization", "Bearer $accessToken")
        }
        try {
            connection.outputStream.use { it.write(JSONObject().put("data", metadata).toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) throw IllegalStateException("Не удалось сохранить данные профиля (${connection.responseCode}).")
        } finally { connection.disconnect() }
    }

    private fun request(path: String, payload: JSONObject): AuthResult {
        if (!isConfigured()) throw IllegalStateException("Supabase не настроен. Укажите SUPABASE_URL и SUPABASE_ANON_KEY в local.properties.")
        val connection = (URL(projectUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $anonKey")
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText() }.orEmpty()
            val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
            if (code !in 200..299) {
                val error = json.optString("msg").ifBlank {
                    json.optString("message").ifBlank { json.optString("error_description").ifBlank { "Ошибка сервера ($code)." } }
                }
                throw IllegalStateException(error)
            }
            AuthResult(json.optString("access_token").takeIf(String::isNotBlank), json.optJSONObject("user")?.optString("id"))
        } finally {
            connection.disconnect()
        }
    }
}
