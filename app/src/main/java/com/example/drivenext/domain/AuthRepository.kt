package com.example.drivenext.domain

interface AuthRepository {
    fun isConfigured(): Boolean
    fun googleOAuthUrl(): String?
    fun signIn(email: String, password: String): AuthSession
    fun register(draft: RegistrationDraft): AuthSession
}
