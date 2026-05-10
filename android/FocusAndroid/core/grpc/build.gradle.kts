import java.net.URI
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.protobuf)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

val serverBaseUrl = localProperties
    .getProperty("serverBaseUrl")
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: throw GradleException("local.properties에 serverBaseUrl을 설정해야 합니다.")

val grpcServerHost = localProperties
    .getProperty("grpcServerHost")
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: URI(serverBaseUrl).host
    ?: throw GradleException("gRPC 서버 호스트를 확인할 수 없습니다.")

val grpcServerPort = localProperties
    .getProperty("grpcServerPort", "50051")
    .trim()
    .toIntOrNull()
    ?: 50051

android {
    namespace = "com.kmu_focus.focusandroid.core.grpc"
    compileSdk = 36

    defaultConfig {
        minSdk = 35
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "GRPC_SERVER_HOST", "\"$grpcServerHost\"")
        buildConfigField("int", "GRPC_SERVER_PORT", grpcServerPort.toString())
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            all {
                it.systemProperty("focus.test.mode", "true")
            }
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
        }
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") {
                    option("lite")
                }
            }
            plugins {
                create("grpc") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(project(":core:metadata"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    api(libs.grpc.okhttp)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.javalite)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
