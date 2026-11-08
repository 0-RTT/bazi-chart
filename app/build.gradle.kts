plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.bazi.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bazi.chart"
        // minSdk 取 26：核心引擎使用 java.time，26 起原生支持，免去 desugaring 依赖
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "0.8.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // app 同时用到分析层（命盘表的神煞行）与盘面文本层。
    // core:interpret 已不依赖 core:analysis，这条传递依赖不再成立，
    // 必须在这里显式声明——依赖谁就写谁，不吃别人的传递依赖。
    implementation(project(":core:analysis"))
    implementation(project(":core:interpret"))
}
