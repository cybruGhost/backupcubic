plugins {
    id("com.android.library")
    kotlin("android")
    @Suppress("DSL_SCOPE_VIOLATION")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.metrolist.innertube"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        create("full") {
            initWith(getByName("release"))
        }
        create("beta") {
            initWith(getByName("release"))
        }
        create("minified") {
            initWith(getByName("release"))
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)
    implementation(libs.timber)
    implementation(libs.newpipe.extractor)
}
