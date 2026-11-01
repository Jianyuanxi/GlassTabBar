plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    `maven-publish`
}

group = "com.github.Jianyuanxi"
version = "1.1.0"

android {
    namespace = "io.github.jianyuanxi.glasstabbar"
    compileSdk = 36

    defaultConfig {
        // 折射、色散靠 RuntimeShader（Android 13 起才有）
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // 调用方要用 LayerBackdrop / layerBackdrop 给页面录层，所以以 api 暴露。
    // 2.x 要求 compileSdk 37 + Kotlin 2.4 / Compose 1.12，先钉 1.0.6
    api("io.github.kyant0:backdrop:1.0.6")
    // 水滴外形用它的连续曲率胶囊；backdrop 依赖它但编译期不暴露
    implementation("io.github.kyant0:shapes:1.2.0")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.Jianyuanxi"
            artifactId = "GlassTabBar"
            version = project.version.toString()
            afterEvaluate { from(components["release"]) }
        }
    }
}
