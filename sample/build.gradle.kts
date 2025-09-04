plugins {
    id("com.android.application")// version "8.12.0"
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    alias(libs.plugins.ksp)

}

android {
    namespace = "io.github.arashiyama11.composemark.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.arashiyama11.composemark.sample"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

configurations.matching { it.name.endsWith("ProcessorClasspath") }.configureEach {
    // Skiko の variant 属性 ui に "awt" をセット
    attributes.attribute(Attribute.of("ui", String::class.java), "awt")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.core.ktx)
    implementation("androidx.activity:activity-compose:1.10.1")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // AndroidX Compose

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.skiko:skiko-awt:0.9.22")
    implementation("com.mikepenz:multiplatform-markdown-renderer:0.35.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-android:0.35.0")

    //implementation(project(":core"))
    implementation(project(":core"))
    ksp(project(":processor"))

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

//    constraints {
//        implementation("org.jetbrains.skiko:skiko:0.9.17")   // 強制上書き
//    }
}

allprojects {
    afterEvaluate {
        configurations.configureEach {
            // ui 属性が未設定なら awt を指定
            if (attributes.getAttribute(Attribute.of("ui", String::class.java)) == null) {
                attributes.attribute(Attribute.of("ui", String::class.java), "awt")
            }
        }
    }
}