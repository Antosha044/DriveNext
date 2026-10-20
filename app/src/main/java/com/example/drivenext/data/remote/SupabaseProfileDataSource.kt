package com.example.drivenext.data.remote

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SupabaseProfileDataSource(private val projectUrl: String, private val anonKey: String) {
    fun upsert(accessToken: String, profile: JSONObject) {
        val connection = (URL(projectUrl.trimEnd('/') + "/rest/v1/users?on_conflict=id").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 12_000; readTimeout = 12_000; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("apikey", anonKey); setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
        }
        try {
            connection.outputStream.use { it.write(profile.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Не удалось сохранить профиль пользователя в Supabase ($code). ${detail.take(240)}")
            }
        } finally { connection.disconnect() }
    }
}
