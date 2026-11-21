import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "app.xiaobuswipe"
    // libxposed interface:102.0.0 要求 compileSdk >= 37（Android 17 预览平台，需在 SDK Manager 中安装）
    compileSdk = 37

    defaultConfig {
        applicationId = "app.xiaobuswipe"
        minSdk = 28
        targetSdk = 35
        // 版本号统一由根目录 gradle.properties 的 VERSION_CODE / VERSION_NAME 提供，
        // 避免硬编码导致覆盖安装后系统版本号不变、LSPosed 无法识别模块更新。
        versionCode = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("VERSION_NAME") as String?) ?: "1.0.0"
    }

    // 仓库根目录存在 keystore.properties 时使用正式签名，否则回退 debug 签名，
    // 保证 clone 后可直接 ./gradlew assembleRelease 构建出可安装包。
    val releaseKeystore = rootProject.file("keystore.properties")
    signingConfigs {
        if (releaseKeystore.exists()) {
            val props = Properties().apply {
                releaseKeystore.inputStream().use { load(it) }
            }
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }
    val activeSigning = if (releaseKeystore.exists()) {
        signingConfigs.getByName("release")
    } else {
        signingConfigs.getByName("debug")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = activeSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            // libxposed 模块元数据必须原样保留（module.prop / scope.list / java_init.list）
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // libxposed API 102：以本地 AAR 形式随仓库提供，避免依赖仓库之外的绝对路径
    compileOnly(files("libs/api-102.0.0.aar"))
    testImplementation("junit:junit:4.13.2")
}
