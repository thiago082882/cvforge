plugins {
    alias(libs.plugins.androidApplication) // AGP 9: Kotlin embutido, NÃO aplicar kotlin-android
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.cvforge.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cvforge"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        // Produção: defina -PapiUrl=https://api.seudominio.com (ou em gradle.properties / CI).
        val prodUrl = (project.findProperty("apiUrl") as String?) ?: "https://api.cvforge.app"
        buildConfigField("String", "API_URL", "\"$prodUrl\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_URL", "\"http://10.0.2.2:8080\"") // emulador → localhost do PC
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
}
