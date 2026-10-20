package com.example.drivenext.data

import com.example.drivenext.data.remote.AuthResult
import com.example.drivenext.data.remote.SupabaseAuthDataSource
import com.example.drivenext.data.remote.SupabaseProfileDataSource
import com.example.drivenext.data.remote.SupabaseStorageDataSource
import com.example.drivenext.domain.AuthRepository
import com.example.drivenext.domain.AuthSession
import com.example.drivenext.domain.RegistrationDraft
import android.content.ContentResolver
import android.net.Uri
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class AuthRepositoryImpl(
    private val remote: SupabaseAuthDataSource,
    private val storage: SupabaseStorageDataSource,
    private val profiles: SupabaseProfileDataSource,
    private val contentResolver: ContentResolver
) : AuthRepository {
    override fun isConfigured() = remote.isConfigured()
    override fun googleOAuthUrl() = remote.googleOAuthUrl()
    override fun signIn(email: String, password: String): AuthSession = remote.signIn(email, password).toSession()

    override fun register(draft: RegistrationDraft): AuthSession {
        val metadata = JSONObject()
            .put("first_name", draft.firstName).put("last_name", draft.lastName)
            .put("middle_name", draft.middleName).put("birth_date", draft.birthDate)
            .put("gender", draft.gender).put("license_number", draft.licenseNumber)
            .put("license_issue_date", draft.licenseIssueDate)
        val result = remote.signUp(draft.email, draft.password, metadata)
        val session = result.toSession()
        if (session.accessToken != null && session.userId != null) {
            val prefix = session.userId
            draft.licensePhotoUri?.let { storage.upload(contentResolver, Uri.parse(it), session.accessToken, "$prefix/license.jpg") }
            draft.passportPhotoUri?.let { storage.upload(contentResolver, Uri.parse(it), session.accessToken, "$prefix/passport.jpg") }
            draft.profilePhotoUri?.let { storage.upload(contentResolver, Uri.parse(it), session.accessToken, "$prefix/profile.jpg") }
            metadata.put("license_photo_path", "$prefix/license.jpg")
                .put("passport_photo_path", "$prefix/passport.jpg")
                .put("profile_photo_path", if (draft.profilePhotoUri == null) JSONObject.NULL else "$prefix/profile.jpg")
            remote.updateUserMetadata(session.accessToken, metadata)
            profiles.upsert(session.accessToken, JSONObject()
                .put("id", session.userId).put("email", draft.email.trim())
                .put("first_name", draft.firstName).put("last_name", draft.lastName)
                .put("middle_name", draft.middleName).put("birth_date", dateForPostgrest(draft.birthDate))
                .put("gender", draft.gender).put("license_number", draft.licenseNumber)
                .put("license_issue_date", dateForPostgrest(draft.licenseIssueDate))
                .put("license_photo_path", "$prefix/license.jpg")
                .put("passport_photo_path", "$prefix/passport.jpg")
                .put("profile_photo_path", if (draft.profilePhotoUri == null) JSONObject.NULL else "$prefix/profile.jpg"))
        }
        return session
    }

    private fun AuthResult.toSession() = AuthSession(accessToken, userId)

    private fun dateForPostgrest(value: String): String {
        val source = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply { isLenient = false }
        val parsed = source.parse(value) ?: throw IllegalArgumentException("Некорректная дата: $value")
        return SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(parsed)
    }
}
