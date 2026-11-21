plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.deivide.webtoontranslator"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        // Mesmo identificador do app desktop.
        applicationId = "com.deivide.webtoontranslator"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

// Ponte Rust: a .so vai pros jniLibs e os bindings Kotlin gerados entram como código-fonte.
val rustOut = layout.buildDirectory.dir("rust")
val buildRust = tasks.register<Exec>("buildRust") {
    workingDir = rootDir
    commandLine("bash", "build-rust.sh")
}

// Modelos do OCR e dicionário da pronúncia: os mesmos arquivos do desktop, copiados pros assets.
abstract class CopyModelsTask : DefaultTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        sources.forEach { it.copyTo(File(out, it.name), overwrite = true) }
    }
}

val copyModels = tasks.register<CopyModelsTask>("copyModels") {
    sources.from(
        "../../src-tauri/ocr-models/text-detection.onnx",
        "../../src-tauri/ocr-models/text-recognition.onnx",
        "../../src-tauri/tts-models/lexicon-en-us.txt",
    )
    outputDir = layout.buildDirectory.dir("generated/models")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyModels, CopyModelsTask::outputDir)
        variant.sources.jniLibs?.addStaticSourceDirectory(rustOut.get().dir("jniLibs").asFile.path)
        variant.sources.kotlin?.addStaticSourceDirectory(rustOut.get().dir("kotlin").asFile.path)
    }
}

tasks.named("preBuild") { dependsOn(buildRust) }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation("${libs.jna.get()}@aar")
}
