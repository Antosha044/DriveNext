package com.example.drivenext.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.drivenext.DriveNextApplication
import com.example.drivenext.domain.AuthSession
import com.example.drivenext.domain.RegistrationDraft
import java.util.concurrent.Executors

class DriveNextViewModel(application: Application) : AndroidViewModel(application) {
    val draft = RegistrationDraft()
    var route: String = ""
    var loginEmail: String = ""
    var loginPassword: String = ""
    val busy = MutableLiveData(false)
    val message = MutableLiveData<String?>()
    private val worker = Executors.newSingleThreadExecutor()
    private val preferences = application.getSharedPreferences("drivenext_preferences", 0)
    private val auth = (application as DriveNextApplication).authUseCases

    fun googleOAuthUrl() = auth.googleOAuthUrl()

    fun signIn(onSuccess: () -> Unit) {
        if (loginEmail.isBlank() || loginPassword.isBlank()) {
            message.value = "Заполните электронную почту и пароль."
            return
        }
        if (!RegistrationDraft().apply { email = loginEmail }.validEmail()) {
            message.value = "Введите корректный адрес электронной почты."
            return
        }
        performAuth({ auth.signIn(loginEmail, loginPassword) }, onSuccess)
    }

    fun completeRegistration(onSuccess: () -> Unit) {
        if (!draft.validStepOne() || !draft.validStepTwo() || !draft.validStepThree()) {
            message.value = "Пожалуйста, проверьте обязательные поля и загруженные фото."
            return
        }
        performAuth({ auth.register(draft) }, onSuccess)
    }

    private fun performAuth(call: () -> AuthSession, onSuccess: () -> Unit) {
        busy.value = true
        worker.execute {
            try {
                val session = call()
                session.accessToken?.let { preferences.edit().putString("access_token", it).apply() }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    busy.value = false
                    if (session.accessToken == null) {
                        message.value = "Аккаунт создан. Подтвердите электронную почту и затем войдите в приложение."
                    }
                    onSuccess()
                }
            } catch (error: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    busy.value = false
                    message.value = error.message ?: "Не удалось выполнить запрос. Проверьте подключение и повторите попытку."
                }
            }
        }
    }

    fun savePhoto(key: String, uri: String) {
        when (key) {
            "profile" -> draft.profilePhotoUri = uri
            "license" -> draft.licensePhotoUri = uri
            else -> draft.passportPhotoUri = uri
        }
    }

    override fun onCleared() {
        worker.shutdownNow()
        super.onCleared()
    }
}
