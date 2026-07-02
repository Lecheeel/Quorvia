import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val signingProperties = Properties().apply {
    val file = rootProject.file("signing.properties")
    if (file.exists()) {
        file.inputStream().use { stream -> load(stream) }
    }
}

val rootLocalEnvProperties = Properties().apply {
    val file = rootProject.file("../.quorvia.local.env")
    if (file.exists()) {
        file.readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
            .forEach { line ->
                val key = line.substringBefore("=").trim()
                val value = line.substringAfter("=").trim()
                setProperty(key, value)
            }
    }
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { stream -> load(stream) }
    }
}

fun secretProperty(vararg names: String): String? =
    names.firstNotNullOfOrNull { name ->
        providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: rootLocalEnvProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: signingProperties.getProperty(name)?.takeIf { it.isNotBlank() }
    }

fun hasReleaseSigning(): Boolean =
    listOf(
        releaseKeystorePath,
        releaseKeyAlias,
        releaseStorePassword,
        releaseKeyPassword,
    ).all { it != null }

val releaseKeystorePath: String?
    get() = secretProperty("ANDROID_RELEASE_KEYSTORE_PATH", "QUORVIA_RELEASE_KEYSTORE_PATH")

val releaseKeyAlias: String?
    get() = secretProperty("ANDROID_RELEASE_KEY_ALIAS", "QUORVIA_RELEASE_KEY_ALIAS")

val releaseStorePassword: String?
    get() = secretProperty("ANDROID_RELEASE_STORE_PASSWORD", "QUORVIA_RELEASE_STORE_PASSWORD", "ANDROID_RELEASE_KEY_PASSWORD", "QUORVIA_RELEASE_KEY_PASSWORD")

val releaseKeyPassword: String?
    get() = secretProperty("ANDROID_RELEASE_KEY_PASSWORD", "QUORVIA_RELEASE_KEY_PASSWORD", "ANDROID_RELEASE_STORE_PASSWORD", "QUORVIA_RELEASE_STORE_PASSWORD")

android {
    namespace = "com.quorvia.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.quorvia.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.4"

        manifestPlaceholders["AMAP_ANDROID_KEY"] =
            secretProperty("AMAP_ANDROID_KEY", "AMAP_ANDROID_RELEASE_KEY", "AMAP_ANDROID_DEBUG_KEY").orEmpty()
        buildConfigField(
            "String",
            "QRNG_PROXY_BASE_URL",
            "\"${secretProperty("QRNG_PROXY_BASE_URL").orEmpty()}\"",
        )
    }

    signingConfigs {
        create("release") {
            val keystorePath = releaseKeystorePath
            val keyAliasValue = releaseKeyAlias
            val storePasswordValue = releaseStorePassword
            val keyPasswordValue = releaseKeyPassword

            if (
                keystorePath != null &&
                keyAliasValue != null &&
                storePasswordValue != null &&
                keyPasswordValue != null
            ) {
                storeFile = file(keystorePath)
                keyAlias = keyAliasValue
                storePassword = storePasswordValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.amap.map3d.location.search)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
}
