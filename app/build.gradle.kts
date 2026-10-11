plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 版本号唯一来源：根目录 gradle.properties（appVersionName / appVersionCode）
val appVersionName: String = providers.gradleProperty("appVersionName").getOrElse("0.0.0")
val appVersionCode: Int = providers.gradleProperty("appVersionCode").getOrElse("1").toInt()

android {
    namespace = "com.zy.homeworklog"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zy.homeworklog"
        minSdk = 29
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "com.zy.homeworklog.AttachmentSmokeInstrumentation"
    }

    // 可通过 debugKeystorePath 指定已有调试密钥，以保持本地安装包的签名身份。
    providers.gradleProperty("debugKeystorePath").orNull?.let { path ->
        signingConfigs.getByName("debug").storeFile = file(path)
    }

    buildTypes {
        release {
            // 个人安装默认沿用 debug 签名，便于覆盖更新且保留数据；启用 R8 与资源裁剪。
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    testBuildType = "release"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
}
