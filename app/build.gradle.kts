plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.samsungproject"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.samsungproject"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Подпись debug-ключом: APK можно ставить вручную (Telegram/USB). Для Play Store — свой keystore.
            signingConfig = signingConfigs.getByName("debug")
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

afterEvaluate {
    listOf("assembleRelease", "assembleDebug").forEach { taskName ->
        tasks.named(taskName).configure {
            doLast {
                val buildType = if (taskName.endsWith("Release")) "release" else "debug"
                val apkDir = layout.buildDirectory.get().asFile.resolve("outputs/apk/$buildType")
                val source = apkDir.listFiles()
                    ?.filter { it.isFile && it.extension.equals("apk", true) }
                    ?.maxByOrNull { it.lastModified() }
                    ?: return@doLast
                val fileName = if (buildType == "release") "LibBook.apk" else "LibBook-debug.apk"
                val outDir = rootProject.layout.projectDirectory.dir("apk").asFile
                outDir.mkdirs()
                val target = File(outDir, fileName)
                source.copyTo(target, overwrite = true)
                logger.lifecycle("LibBook APK: ${target.absolutePath}")
            }
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.android.image.cropper)
    implementation(libs.pdfbox.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}