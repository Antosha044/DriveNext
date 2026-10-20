package com.example.drivenext.domain

class AuthUseCases(private val repository: AuthRepository) {
    fun isConfigured() = repository.isConfigured()
    fun googleOAuthUrl() = repository.googleOAuthUrl()
    fun signIn(email: String, password: String) = repository.signIn(email, password)
    fun register(draft: RegistrationDraft) = repository.register(draft)
}
