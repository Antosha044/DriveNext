package com.example.drivenext

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/** Initial application flow: splash, connectivity gate and first-run onboarding. */
class MainActivity : AppCompatActivity() {
    private val purple = Color.rgb(45, 20, 73)
    private val ink = Color.rgb(30, 31, 36)
    private val muted = Color.rgb(105, 108, 119)
    private lateinit var host: FrameLayout
    private var slide = 0
    private var gestureStartX = 0f
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
            showWelcome()
        }
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
        page.gravity = Gravity.CENTER
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
        page.addView(content, matchWrap())
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
        // Auth and registration routes are implemented in the second laboratory.
        when (destination) {
            "login" -> showLogin()
            "register1" -> showRegistrationStep1()
        }
    }

    private fun showLogin() = showWelcome()
    private fun showRegistrationStep1() = showWelcome()

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
