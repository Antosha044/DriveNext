import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val privateConfig = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun quotedBuildConfigValue(name: String): String {
    val defaultValue = if (name == "SUPABASE_STORAGE_BUCKET") "documents" else ""
    val value = privateConfig.getProperty(name, defaultValue)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$value\""
}

android {
    namespace = "com.example.drivenext"
    buildFeatures { buildConfig = true }
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.drivenext"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SUPABASE_URL", quotedBuildConfigValue("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_ANON_KEY", quotedBuildConfigValue("SUPABASE_ANON_KEY"))
        buildConfigField("String", "SUPABASE_STORAGE_BUCKET", quotedBuildConfigValue("SUPABASE_STORAGE_BUCKET"))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
