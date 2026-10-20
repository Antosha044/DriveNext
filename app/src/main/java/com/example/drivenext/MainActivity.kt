package com.example.drivenext

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.viewModels
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Observer
import com.example.drivenext.presentation.DriveNextViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val model: DriveNextViewModel by viewModels()
    private val purple = Color.rgb(45, 20, 73)
    private val ink = Color.rgb(30, 31, 36)
    private val muted = Color.rgb(105, 108, 119)
    private lateinit var host: FrameLayout
    private var slide = 0
    private var gestureStartX = 0f
    private var pendingPhotoKey = ""
    private var loadingDialog: androidx.appcompat.app.AlertDialog? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            model.savePhoto(pendingPhotoKey, uri.toString())
            if (model.route == "register3") showRegistrationStep3()
        }
    }
    private val slides = listOf(
        OnboardingSlide(R.drawable.onboarding_rent, "Аренда автомобилей", "Открой для себя удобный и доступный способ передвижения"),
        OnboardingSlide(R.drawable.onboarding_safe, "Безопасно и удобно", "Арендуй автомобиль и наслаждайся его удобством"),
        OnboardingSlide(R.drawable.onboarding_offers, "Лучшие предложения", "Выбирай понравившееся среди сотен доступных автомобилей")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        host = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        setContentView(host)
        showSplashPage()
        model.message.observe(this, Observer { message ->
            if (!message.isNullOrBlank()) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage(message).setPositiveButton("Понятно", null).show()
                model.message.value = null
            }
        })
        model.busy.observe(this, Observer { busy -> updateBusyState(busy == true) })
        if (intent?.data?.scheme == "drivenext") { handleOAuthCallback(intent.data); return }
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            if (!hasInternet()) showNoConnection() else continueStartup()
        }, 2200L)
    }

    private fun continueStartup() {
        val preferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)) {
            slide = 0
            showOnboarding()
        } else {
            val token = preferences.getString("access_token", null)
            if (token != null && isJwtUnexpired(token)) showHome() else showWelcome()
        }
    }

    private fun showSplashPage() {
        val content = column().apply { setPadding(dp(28)); gravity = Gravity.CENTER_HORIZONTAL }
        content.addView(label("DriveNext", 30f, purple, Typeface.BOLD, Gravity.START).withTop(76))
        content.addView(label("Поможем найти твою следующую поездку", 17f, muted, Typeface.NORMAL, Gravity.START).withTop(12))
        val artwork = ImageView(this).apply {
            setImageResource(R.drawable.splash_illustration); scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Арендатор и владелец автомобиля"
        }
        content.addView(artwork, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(28); bottomMargin = dp(24) })
        host.removeAllViews(); host.addView(content, FrameLayout.LayoutParams(-1, -1))
    }

    private fun hasInternet(): Boolean = try {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: SecurityException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    private fun showNoConnection() {
        val page = page()
        val content = column().apply {
            gravity = Gravity.CENTER
            setPadding(dp(28))
        }
        content.addView(label("⌁", 70f, purple, Typeface.NORMAL, Gravity.CENTER))
        content.addView(label("Нет подключения к интернету", 22f, ink, Typeface.BOLD, Gravity.CENTER).withTop(18))
        content.addView(label("Проверьте подключение и повторите попытку", 16f, muted, Typeface.NORMAL, Gravity.CENTER).withTop(10))
        content.addView(primaryButton("Повторить попытку") {
            if (hasInternet()) continueStartup() else showNoConnection()
        }.withTop(28))
        page.addView(content, FrameLayout.LayoutParams(-1, -1))
        host.removeAllViews(); host.addView(page)
    }

    private fun showOnboarding() {
        val page = page().apply {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> gestureStartX = event.x
                    MotionEvent.ACTION_UP -> {
                        val distance = event.x - gestureStartX
                        if (kotlin.math.abs(distance) > dp(70)) {
                            if (distance < 0 && slide < slides.lastIndex) { slide++; showOnboarding() }
                            if (distance > 0 && slide > 0) { slide--; showOnboarding() }
                        }
                    }
                }
                true
            }
        }
        val content = column().apply { setPadding(dp(28)); gravity = Gravity.CENTER_HORIZONTAL }
        val skip = label("Пропустить", 16f, purple, Typeface.BOLD, Gravity.END).apply {
            setOnClickListener { finishOnboarding() }
        }
        content.addView(skip, LinearLayout.LayoutParams(-1, dp(48)).apply { gravity = Gravity.END })
        val illustration = ImageView(this).apply {
            setImageResource(slides[slide].image)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = slides[slide].title
        }
        content.addView(illustration, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(4); bottomMargin = dp(12)
        })
        content.addView(label(slides[slide].title, 28f, Color.BLACK, Typeface.BOLD, Gravity.START))
        content.addView(label(slides[slide].description, 17f, ink, Typeface.NORMAL, Gravity.START).withTop(18))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(34), 0, dp(10))
        }
        val indicators = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        slides.indices.forEach { i ->
            val dot = View(this).apply { setBackgroundColor(if (i == slide) purple else Color.rgb(226, 229, 233)) }
            indicators.addView(dot, LinearLayout.LayoutParams(if (i == slide) dp(36) else dp(14), dp(10)).apply { marginEnd = dp(8) })
        }
        controls.addView(indicators, LinearLayout.LayoutParams(0, dp(56), 1f))
        controls.addView(primaryButton(if (slide == slides.lastIndex) "Поехали" else "Далее") {
            if (slide == slides.lastIndex) finishOnboarding() else { slide++; showOnboarding() }
        }, LinearLayout.LayoutParams(dp(154), dp(58)))
        content.addView(controls)
        page.addView(content, FrameLayout.LayoutParams(-1, -1))
        host.removeAllViews(); host.addView(page)
    }

    private fun finishOnboarding() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
        showWelcome()
    }

    private fun showWelcome() {
        val page = page()
        val content = column().apply { setPadding(dp(28)); gravity = Gravity.CENTER_HORIZONTAL }
        content.addView(label("DriveNext", 36f, purple, Typeface.BOLD, Gravity.START).withTop(34))
        content.addView(label("Поможем найти твою следующую поездку", 18f, muted, Typeface.NORMAL, Gravity.START).withTop(10))
        val art = ImageView(this).apply { setImageResource(R.drawable.onboarding_rent); scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Аренда автомобиля" }
        content.addView(art, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(28); bottomMargin = dp(18) })
        content.addView(primaryButton("Войти") { showAuth("login") }.withTop(8))
        content.addView(secondaryButton("Зарегистрироваться") { showAuth("register1") }.withTop(16))
        page.addView(content, FrameLayout.LayoutParams(-1, -1))
        host.removeAllViews(); host.addView(page)
    }

    private fun showAuth(destination: String) {
        when (destination) {
            "login" -> showLogin()
            "register1" -> showRegistrationStep1()
        }
    }

    private var submitButton: Button? = null
    private fun updateBusyState(busy: Boolean) {
        submitButton?.let { it.isEnabled = !busy; it.text = if (busy) "Загрузка…" else it.tag as? String ?: it.text }
        if (busy && loadingDialog?.isShowing != true) {
            val indicator = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(24), dp(18), dp(24), dp(18))
                addView(android.widget.ProgressBar(this@MainActivity), LinearLayout.LayoutParams(dp(32), dp(32)))
                addView(TextView(this@MainActivity).apply { text = "  Выполняется запрос…"; textSize = 15f }, LinearLayout.LayoutParams(-2, -2))
            }
            loadingDialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(indicator).setCancelable(false).create()
            loadingDialog?.show()
        } else if (!busy) loadingDialog?.dismiss()
    }

    private fun showLogin() {
        model.route = "login"
        val page = page()
        val root = column()
        root.addView(header("Войдите в аккаунт", "Назад") { showWelcome() })
        root.addView(label("Пожалуйста, введите свои данные", 14f, muted, Typeface.NORMAL, Gravity.CENTER).withTop(4))
        val body = column().apply { setPadding(dp(22), dp(46), dp(22), dp(18)) }
        body.addView(fieldCaption("Электронная почта"))
        val email = textField("Введите электронную почту", model.loginEmail, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        body.addView(email)
        body.addView(fieldCaption("Пароль").withTop(18))
        val password = textField("Введите пароль", model.loginPassword, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val passwordBox = TextInputLayout(this).apply { endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE; addView(password) }
        body.addView(passwordBox)
        val forgot = label("Забыли пароль?", 14f, purple, Typeface.NORMAL, Gravity.CENTER).withTop(14)
        body.addView(forgot)
        email.afterTextChanged { model.loginEmail = it }
        password.afterTextChanged { model.loginPassword = it }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = column().apply { setPadding(dp(22), dp(8), dp(22), dp(28)) }
        val signIn = primaryButton("Войти") { model.signIn { showHome() } }.apply {
            tag = "Войти"; isEnabled = model.loginEmail.isNotBlank() && model.loginPassword.isNotBlank()
        }
        submitButton = signIn
        email.afterTextChanged { signIn.isEnabled = model.loginEmail.isNotBlank() && model.loginPassword.isNotBlank() }
        password.afterTextChanged { signIn.isEnabled = model.loginEmail.isNotBlank() && model.loginPassword.isNotBlank() }
        actions.addView(signIn, LinearLayout.LayoutParams(-1, dp(54)))
        actions.addView(secondaryButton("Войти через Google") { startGoogleOAuth() }.withTop(12))
        actions.addView(label("Зарегистрироваться", 15f, purple, Typeface.BOLD, Gravity.CENTER).apply {
            setPadding(0, dp(18), 0, dp(10)); setOnClickListener { showRegistrationStep1() }
        })
        root.addView(actions)
        page.addView(root, FrameLayout.LayoutParams(-1, -1)); host.removeAllViews(); host.addView(page)
    }

    private fun showRegistrationStep1() {
        model.route = "register1"
        val draft = model.draft
        val page = page(); val root = column()
        val body = column().apply { setPadding(dp(22), dp(4), dp(22), dp(18)) }
        body.addView(header("Создать аккаунт", "Назад") { showWelcome() })
        body.addView(fieldCaption("Электронная почта").withTop(54))
        val email = textField("Введите электронную почту", draft.email, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        body.addView(email)
        body.addView(fieldCaption("Придумайте пароль").withTop(16))
        val password = textField("Введите пароль", draft.password, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        body.addView(TextInputLayout(this).apply { endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE; addView(password) })
        body.addView(fieldCaption("Повторите пароль").withTop(16))
        val repeat = textField("Введите пароль", draft.repeatedPassword, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        body.addView(TextInputLayout(this).apply { endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE; addView(repeat) })
        val consent = CheckBox(this).apply {
            text = "Я согласен с условиями обслуживания и политикой конфиденциальности"
            textSize = 14f; setTextColor(ink); isChecked = draft.termsAccepted
            setPadding(0, dp(10), 0, dp(10)); setOnCheckedChangeListener { _, checked -> draft.termsAccepted = checked }
        }
        body.addView(consent.withTop(22))
        email.afterTextChanged { draft.email = it }
        password.afterTextChanged { draft.password = it }
        repeat.afterTextChanged { draft.repeatedPassword = it }
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val next = primaryButton("Далее") {
            when {
                !draft.validEmail() -> model.message.value = "Введите корректный адрес электронной почты."
                draft.password.isBlank() || draft.repeatedPassword.isBlank() -> model.message.value = "Заполните пароль и его подтверждение."
                draft.password != draft.repeatedPassword -> model.message.value = "Пароли не совпадают."
                !draft.termsAccepted -> model.message.value = "Необходимо согласиться с условиями обслуживания и политикой конфиденциальности."
                else -> showRegistrationStep2()
            }
        }.apply { tag = "Далее" }
        submitButton = next
        root.addView(next, LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(dp(22), dp(10), dp(22), dp(26)) })
        page.addView(root); host.removeAllViews(); host.addView(page)
    }

    private fun showRegistrationStep2() {
        model.route = "register2"
        val draft = model.draft
        val page = page(); val root = column()
        val body = column().apply { setPadding(dp(22), dp(4), dp(22), dp(18)) }
        body.addView(header("Создать аккаунт", "Назад") { showRegistrationStep1() })
        body.addView(fieldCaption("Фамилия").withTop(24))
        val last = textField("Введите фамилию", draft.lastName); body.addView(last)
        body.addView(fieldCaption("Имя").withTop(14))
        val first = textField("Введите имя", draft.firstName); body.addView(first)
        body.addView(fieldCaption("Отчество").withTop(14))
        val middle = textField("Введите отчество", draft.middleName); body.addView(middle)
        body.addView(fieldCaption("Дата рождения").withTop(14))
        val birth = dateField("DD/MM/YYYY", draft.birthDate, "Дата рождения"); body.addView(birth)
        body.addView(fieldCaption("Пол").withTop(14))
        val gender = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            val male = RadioButton(this@MainActivity).apply { id = View.generateViewId(); text = "Мужской"; isChecked = draft.gender == "Мужской" }
            val female = RadioButton(this@MainActivity).apply { id = View.generateViewId(); text = "Женский"; isChecked = draft.gender == "Женский" }
            addView(male); addView(female); setOnCheckedChangeListener { group, checked -> draft.gender = group.findViewById<RadioButton>(checked)?.text?.toString() ?: "" }
        }
        body.addView(gender)
        last.afterTextChanged { draft.lastName = it }; first.afterTextChanged { draft.firstName = it }
        middle.afterTextChanged { draft.middleName = it }; birth.afterTextChanged { draft.birthDate = it }
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(primaryButton("Далее") {
            when {
                draft.lastName.isBlank() || draft.firstName.isBlank() || draft.birthDate.isBlank() -> model.message.value = "Пожалуйста, заполните все обязательные поля."
                !com.example.drivenext.domain.RegistrationDraft.isValidDate(draft.birthDate) -> model.message.value = "Введите корректную дату рождения."
                else -> showRegistrationStep3()
            }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(dp(22), dp(10), dp(22), dp(26)) })
        page.addView(root); host.removeAllViews(); host.addView(page)
    }

    private fun showRegistrationStep3() {
        model.route = "register3"
        val draft = model.draft
        val page = page(); val root = column()
        val body = column().apply { setPadding(dp(22), dp(4), dp(22), dp(16)) }
        body.addView(header("Создать аккаунт", "Назад") { showRegistrationStep2() })
        val profile = TextView(this).apply {
            text = if (draft.profilePhotoUri == null) "◎\n＋" else "Фото профиля добавлено ✓"
            textSize = 28f; gravity = Gravity.CENTER; setTextColor(purple)
            setOnClickListener { choosePhoto("profile") }
        }
        body.addView(profile, LinearLayout.LayoutParams(-1, dp(112)).apply { topMargin = dp(12) })
        body.addView(label("Добавление фотографии поможет владельцам и арендаторам узнать друг друга, когда они будут забирать машину", 14f, ink, Typeface.NORMAL, Gravity.START).withTop(8))
        body.addView(fieldCaption("Номер водительского удостоверения").withTop(14))
        val license = textField("0000000000", draft.licenseNumber, InputType.TYPE_CLASS_TEXT)
        license.filters = arrayOf(android.text.InputFilter.LengthFilter(10)); body.addView(license)
        body.addView(fieldCaption("Дата выдачи").withTop(14))
        val issue = dateField("DD/MM/YYYY", draft.licenseIssueDate, "Дата выдачи"); body.addView(issue)
        body.addView(fieldCaption("Загрузите фото водительского удостоверения").withTop(14))
        body.addView(uploadButton("Водительское удостоверение", draft.licensePhotoUri != null) { choosePhoto("license") })
        body.addView(fieldCaption("Загрузите фото паспорта").withTop(12))
        body.addView(uploadButton("Паспорт", draft.passportPhotoUri != null) { choosePhoto("passport") })
        license.afterTextChanged { draft.licenseNumber = it }; issue.afterTextChanged { draft.licenseIssueDate = it }
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val finish = primaryButton("Далее") {
            when {
                draft.licenseNumber.length != 10 || draft.licenseIssueDate.isBlank() -> model.message.value = "Пожалуйста, заполните все обязательные поля."
                !com.example.drivenext.domain.RegistrationDraft.isValidDate(draft.licenseIssueDate) -> model.message.value = "Введите корректную дату выдачи."
                draft.licensePhotoUri.isNullOrBlank() || draft.passportPhotoUri.isNullOrBlank() -> model.message.value = "Пожалуйста, загрузите все необходимые фото."
                else -> model.completeRegistration { showSuccess() }
            }
        }
        finish.tag = "Далее"; submitButton = finish
        root.addView(finish, LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(dp(22), dp(10), dp(22), dp(24)) })
        page.addView(root); host.removeAllViews(); host.addView(page)
    }

    private fun showSuccess() {
        model.route = "success"
        val page = page(); val content = column().apply { gravity = Gravity.CENTER; setPadding(dp(24)) }
        content.addView(label("Пользуйтесь с удовольствием", 24f, ink, Typeface.BOLD, Gravity.CENTER))
        content.addView(label("✓", 76f, purple, Typeface.BOLD, Gravity.CENTER).withTop(82))
        content.addView(label("Поздравляем", 28f, ink, Typeface.BOLD, Gravity.CENTER).withTop(30))
        content.addView(label("Вы успешно зарегистрировались. Обработка ваших персональных данных займёт около двух часов. Спасибо за ожидание.", 16f, ink, Typeface.NORMAL, Gravity.CENTER).withTop(22))
        content.addView(primaryButton("Далее") { showHome() }.withTop(72))
        page.addView(content, FrameLayout.LayoutParams(-1, -1)); host.removeAllViews(); host.addView(page)
    }

    private fun showHome() {
        model.route = "home"
        val page = page(); val content = column().apply { gravity = Gravity.CENTER; setPadding(dp(24)) }
        content.addView(label("DriveNext", 34f, purple, Typeface.BOLD, Gravity.CENTER))
        content.addView(label("Добро пожаловать!", 22f, ink, Typeface.NORMAL, Gravity.CENTER).withTop(16))
        content.addView(primaryButton("Продолжить") { showWelcome() }.withTop(36))
        page.addView(content, FrameLayout.LayoutParams(-1, -1)); host.removeAllViews(); host.addView(page)
    }

    private fun startGoogleOAuth() {
        val url = model.googleOAuthUrl()
        if (url == null) { model.message.value = "Для входа через Google настройте Supabase URL и anon key, а также включите Google OAuth в настройках Supabase."; return }
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { model.message.value = "Не удалось открыть страницу авторизации Google." }
    }

    private fun handleOAuthCallback(uri: Uri?) {
        val fragment = uri?.fragment.orEmpty().split('&').mapNotNull { part ->
            val pieces = part.split('=', limit = 2); if (pieces.size == 2) pieces[0] to Uri.decode(pieces[1]) else null
        }.toMap()
        val token = fragment["access_token"]
        if (token != null) { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("access_token", token).apply(); showHome() }
        else { showLogin(); model.message.value = fragment["error_description"] ?: "Авторизация Google не завершена." }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent.data?.scheme == "drivenext") handleOAuthCallback(intent.data)
    }

    private fun isJwtUnexpired(token: String): Boolean = try {
        val payload = token.split('.')[1]
        val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING), Charsets.UTF_8)
        org.json.JSONObject(json).optLong("exp") > System.currentTimeMillis() / 1000
    } catch (_: Exception) { false }

    private fun header(title: String, back: String, onBack: () -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(label("‹", 32f, ink, Typeface.NORMAL, Gravity.CENTER).apply {
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(42), dp(54)))
        row.addView(label(title, 20f, ink, Typeface.BOLD, Gravity.CENTER), LinearLayout.LayoutParams(0, dp(54), 1f))
        row.addView(View(this), LinearLayout.LayoutParams(dp(42), dp(54)))
        return row
    }

    private fun fieldCaption(text: String) = label(text, 15f, muted, Typeface.NORMAL, Gravity.START).apply { setPadding(0, 0, 0, dp(7)) }
    private fun textField(hintText: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(this).apply {
        setSingleLine(true); hint = hintText; setText(value); textSize = 15f; inputType = type
        setTextColor(ink); setHintTextColor(Color.rgb(135, 145, 166)); background = ContextCompat.getDrawable(this@MainActivity, R.drawable.input_background)
        setPadding(dp(14), 0, dp(14), 0); minHeight = dp(50)
    }
    private fun dateField(hint: String, value: String, title: String): EditText = textField(hint, value, InputType.TYPE_CLASS_TEXT).apply {
        isFocusable = false; isClickable = true
        setOnClickListener { openDatePicker(this, title) }
    }
    private fun openDatePicker(field: EditText, title: String) {
        val picker = MaterialDatePicker.Builder.datePicker().setTitleText(title).build()
        picker.addOnPositiveButtonClickListener { millis ->
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            field.setText(formatter.format(Date(millis)))
        }
        picker.show(supportFragmentManager, "date_${title.hashCode()}")
    }
    private fun choosePhoto(key: String) {
        pendingPhotoKey = key
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    private fun uploadButton(name: String, selected: Boolean, action: () -> Unit) = secondaryButton(
        if (selected) "✓  $name загружено" else "⇧  Загрузить фото · $name", action
    )
    private fun EditText.afterTextChanged(onChange: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChange(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun page() = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(text: String, size: Float, color: Int, style: Int, gravityValue: Int) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); typeface = Typeface.create("sans-serif", style); gravity = gravityValue
    }
    private fun primaryButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 17f; setTextColor(Color.WHITE)
        backgroundTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.drivenext_purple)
        setOnClickListener { action() }
    }
    private fun secondaryButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 17f; setTextColor(ink)
        backgroundTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.white)
        setOnClickListener { action() }
    }
    private fun TextView.withTop(margin: Int): TextView = apply {
        layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(margin) }
    }
    private fun Button.withTop(margin: Int): Button = apply {
        layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(margin) }
    }
    private fun matchWrap() = FrameLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private data class OnboardingSlide(val image: Int, val title: String, val description: String)
    private companion object { const val PREFS = "drivenext_preferences"; const val KEY_ONBOARDING_COMPLETE = "onboarding_complete" }
}
