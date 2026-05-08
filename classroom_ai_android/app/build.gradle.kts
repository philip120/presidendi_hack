import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun String.asBuildConfigString(): String {
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

fun String.normalizedLocalPropertyValue(): String {
    val trimmed = trim()
    return when {
        trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"' -> {
            trimmed.substring(1, trimmed.lastIndex)
        }
        trimmed.length >= 2 && trimmed.first() == '\'' && trimmed.last() == '\'' -> {
            trimmed.substring(1, trimmed.lastIndex)
        }
        else -> trimmed
    }
}

android {
    namespace = "com.haridushakk.classroomai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.haridushakk.classroomai"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val geminiApiKey = localProperties
            .getProperty("GEMINI_API_KEY", "")
            .normalizedLocalPropertyValue()
        val geminiModelName = localProperties
            .getProperty("GEMINI_MODEL_NAME", "gemini-3-flash-preview")
            .normalizedLocalPropertyValue()
        buildConfigField("String", "GEMINI_API_KEY", geminiApiKey.asBuildConfigString())
        buildConfigField("String", "GEMINI_MODEL_NAME", geminiModelName.asBuildConfigString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
