import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

val mediaMtxHost = localProperties
    .getProperty("mediaMtxHost")
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: throw GradleException("local.properties에 mediaMtxHost를 설정해야 합니다.")

val mediaMtxPort = localProperties
    .getProperty("mediaMtxPort")
    ?.trim()
    ?.toIntOrNull()
    ?: throw GradleException("local.properties에 mediaMtxPort를 설정해야 합니다.")

android {
    namespace = "com.kmu_focus.focusandroid.feature.broadcast"
    compileSdk = 36

    defaultConfig {
        minSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "MEDIA_MTX_HOST", "\"$mediaMtxHost\"")
        buildConfigField("int", "MEDIA_MTX_PORT", mediaMtxPort.toString())
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            all {
                it.jvmArgs("-Xmx1536m", "-XX:+HeapDumpOnOutOfMemoryError")
                it.systemProperty("focus.test.mode", "true")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(project(":core:network"))
    implementation(project(":core:streaming"))
    implementation(project(":core:grpc"))
    implementation(project(":core:metadata"))
    implementation(project(":core:media"))
    implementation(project(":core:ui"))
    implementation(project(":feature:camera"))

    implementation(libs.retrofit)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling)
}
