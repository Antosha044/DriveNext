package com.example.drivenext

import android.app.Application
import com.example.drivenext.data.AuthRepositoryImpl
import com.example.drivenext.data.remote.SupabaseAuthDataSource
import com.example.drivenext.data.remote.SupabaseProfileDataSource
import com.example.drivenext.data.remote.SupabaseStorageDataSource
import com.example.drivenext.domain.AuthUseCases

class DriveNextApplication : Application() {
    val authUseCases: AuthUseCases by lazy {
        val auth = SupabaseAuthDataSource(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        val storage = SupabaseStorageDataSource(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, BuildConfig.SUPABASE_STORAGE_BUCKET)
        val profiles = SupabaseProfileDataSource(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        AuthUseCases(AuthRepositoryImpl(auth, storage, profiles, contentResolver))
    }
}
