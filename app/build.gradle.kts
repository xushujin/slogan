import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * 版本号来源（按优先级）：
 * 1. -PversionName / -PversionCode（CI 从 tag 与 run_number 注入）
 * 2. gradle.properties 中的同名属性
 * 3. 内置默认值
 */
val appVersionName: String = providers.gradleProperty("versionName").orNull?.takeIf { it.isNotBlank() } ?: "1.0.0"
val appVersionCode: Int = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1

// 单一来源：清单、构建配置、关于页展示都从这里取值，避免文档与代码漂移。
val sloganMinSdk = 26
val sloganTargetSdk = 36
val sloganCompileSdk = 36

/**
 * 签名双通道：CI 环境变量优先，其次本地 keystore.properties。
 * 两者都没有时 release 产物不签名（不会静默使用 debug 签名）。
 *
 * 通过 providers.fileContents 读取，让配置文件内容成为配置缓存的输入：
 * 新建/修改 keystore.properties 后不会命中旧的缓存配置。
 */
val keystoreProperties = Properties().apply {
    val content = providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
        .asText
        .orNull
    if (content != null) {
        load(content.reader())
    }
}

data class SigningMaterial(
    val storePath: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

val signingMaterial: SigningMaterial? = run {
    val envStore = providers.environmentVariable("SLOGAN_KEYSTORE_PATH").orNull
    val envStorePassword = providers.environmentVariable("SLOGAN_KEYSTORE_PASSWORD").orNull
    val envAlias = providers.environmentVariable("SLOGAN_KEY_ALIAS").orNull
    val envKeyPassword = providers.environmentVariable("SLOGAN_KEY_PASSWORD").orNull

    val localStore = keystoreProperties.getProperty("storeFile")
    val localStorePassword = keystoreProperties.getProperty("storePassword")
    val localAlias = keystoreProperties.getProperty("keyAlias")
    val localKeyPassword = keystoreProperties.getProperty("keyPassword")

    val store = envStore?.takeIf(String::isNotBlank) ?: localStore?.takeIf(String::isNotBlank)
    val storePassword = envStorePassword?.takeIf(String::isNotBlank) ?: localStorePassword?.takeIf(String::isNotBlank)
    val alias = envAlias?.takeIf(String::isNotBlank) ?: localAlias?.takeIf(String::isNotBlank)
    val keyPassword = envKeyPassword?.takeIf(String::isNotBlank) ?: localKeyPassword?.takeIf(String::isNotBlank)

    if (store == null || storePassword == null || alias == null || keyPassword == null) {
        null
    } else {
        SigningMaterial(store, storePassword, alias, keyPassword)
    }
}

android {
    namespace = "com.slogan.app"
    compileSdk = sloganCompileSdk

    defaultConfig {
        applicationId = "com.slogan.app"
        minSdk = sloganMinSdk
        targetSdk = sloganTargetSdk
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("int", "MIN_SDK_INT", sloganMinSdk.toString())
        buildConfigField("int", "TARGET_SDK_INT", sloganTargetSdk.toString())
        buildConfigField("String", "KOTLIN_VERSION", "\"${libs.versions.kotlin.get()}\"")
        buildConfigField("String", "COMPOSE_BOM_VERSION", "\"${libs.versions.composeBom.get()}\"")
    }

    signingConfigs {
        if (signingMaterial != null) {
            create("release") {
                storeFile = rootProject.file(signingMaterial.storePath)
                storePassword = signingMaterial.storePassword
                keyAlias = signingMaterial.keyAlias
                keyPassword = signingMaterial.keyPassword
                // minSdk 26 起系统只认 v2+ 签名；v1（JAR 签名）不再生成。
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        // 依赖升级提示与构建质量无关，显式关闭，避免把“有新版本”当成编译告警。
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    testOptions {
        unitTests.all { test ->
            test.testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
        // 显式声明，避免不同 Gradle/JDK 组合下隐式语言版本变化。
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // 仅用于在 JVM 上执行真实的 SQLite 迁移验证，不进入 App 运行时。
    testImplementation(libs.sqlite.jdbc)
}
