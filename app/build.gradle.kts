plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.xl.gpstool"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        versionCode = 29
        versionName = "3.6.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources.excludes.add("META-INF/INDEX.LIST")
        resources.excludes.add("META-INF/io.netty.versions.properties")
        resources.excludes.add("META-INF/DEPENDENCIES")
    }
    buildTypes {
        release {
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
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.baidu.lbsyun:BaiduMapSDK_Map:7.6.4")
    // 👇👇 赶紧把这句“搜索雷达”配件补上！👇👇
    implementation("com.baidu.lbsyun:BaiduMapSDK_Search:7.6.4")
    // 🪄 召唤 Shizuku 魔法引擎 (用于免 Root 底层提权)
    implementation("dev.rikka.shizuku:provider:13.1.5")
}