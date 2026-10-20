package com.example.drivenext.data.remote

import android.content.ContentResolver
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL

class SupabaseStorageDataSource(private val projectUrl: String, private val anonKey: String, private val bucket: String) {
    fun upload(contentResolver: ContentResolver, uri: Uri, accessToken: String, objectPath: String) {
        require(bucket.isNotBlank()) { "Укажите SUPABASE_STORAGE_BUCKET в local.properties." }
        val encodedBucket = Uri.encode(bucket)
        val encodedPath = Uri.encode(objectPath, "/")
        val connection = (URL("${projectUrl.trimEnd('/')}/storage/v1/object/$encodedBucket/$encodedPath").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 30_000; doOutput = true
            setRequestProperty("apikey", anonKey); setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", contentResolver.getType(uri) ?: "image/jpeg")
            setRequestProperty("x-upsert", "true")
        }
        try {
            val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("Не удалось открыть выбранное изображение.")
            input.use { source -> connection.outputStream.use { source.copyTo(it) } }
            if (connection.responseCode !in 200..299) throw IllegalStateException("Не удалось загрузить фотографию в Supabase Storage (${connection.responseCode}).")
        } finally { connection.disconnect() }
    }
}
