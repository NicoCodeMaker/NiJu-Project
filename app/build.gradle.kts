import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// Las credenciales de Supabase se leen de local.properties (no se sube a git).
//   SUPABASE_URL=https://xxxx.supabase.co
//   SUPABASE_ANON_KEY=eyJ...
//   AUTH_GOOGLE_ENABLED=false
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(name: String, default: String = ""): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: default).trim()

android {
    namespace = "com.example.niju_project"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.niju_project"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${localProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("SUPABASE_ANON_KEY")}\"")
        buildConfigField("boolean", "AUTH_GOOGLE_ENABLED", localProp("AUTH_GOOGLE_ENABLED", "false"))

        // Esquema del enlace de retorno del inicio de sesión: niju://login-callback
        manifestPlaceholders["authScheme"] = "niju"
        manifestPlaceholders["authHost"] = "login-callback"
    }

    buildTypes {
        release {
            // Se activará en la etapa de pulido junto con las reglas de R8 para
            // kotlinx.serialization y Ktor.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // ── Supabase ─────────────────────────────────────────────────────────────
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.browser)        // pestaña segura para el login OAuth
    implementation(libs.androidx.work)           // recordatorios diarios

    // ── AndroidX / UI ────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // ── Corrutinas ───────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Pruebas ──────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
