package com.example.drivenext.domain

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

data class RegistrationDraft(
    var email: String = "",
    var password: String = "",
    var repeatedPassword: String = "",
    var termsAccepted: Boolean = false,
    var lastName: String = "",
    var firstName: String = "",
    var middleName: String = "",
    var birthDate: String = "",
    var gender: String = "Женский",
    var licenseNumber: String = "",
    var licenseIssueDate: String = "",
    var profilePhotoUri: String? = null,
    var licensePhotoUri: String? = null,
    var passportPhotoUri: String? = null
) {
    fun validEmail(): Boolean = EMAIL_PATTERN.matches(email.trim())
    fun validStepOne(): Boolean = validEmail() && password.isNotBlank() &&
        password == repeatedPassword && termsAccepted
    fun validStepTwo(): Boolean = lastName.isNotBlank() && firstName.isNotBlank() &&
        isValidDate(birthDate) && gender in setOf("Мужской", "Женский")
    fun validStepThree(): Boolean = licenseNumber.length == 10 && isValidDate(licenseIssueDate) &&
        !licensePhotoUri.isNullOrBlank() && !passportPhotoUri.isNullOrBlank()

    companion object {
        private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.(com|ru)$", RegexOption.IGNORE_CASE)
        fun isValidDate(value: String): Boolean {
            val parser = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply { isLenient = false }
            val position = ParsePosition(0)
            val parsed = parser.parse(value, position)
            return parsed != null && position.index == value.length
        }
    }
}
