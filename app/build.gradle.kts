import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.zenshield.focus"
    minSdk = 29
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    }
  }

  sourceSets {
    getByName("main") {
      jniLibs.directories.add("src/main/jniLibs")
      jniLibs.directories.add("build/rustJniLibs/android")
    }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// ============================================================================
// rust-android-gradle (`org.mozilla.rust-android-gradle.rust-android`) DSL
// Compiles `/rust_core/Cargo.toml` (`libsentinal_rust_core.so`) across Android
// NDK ABI targets (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
// ============================================================================
class RustAndroidCargoSpec {
  var module: String = "../rust_core"
  var libname: String = "sentinal_rust_core"
  var targets: List<String> = listOf("arm64", "arm", "x86", "x86_64")
  var profile: String = "release"
  var targetDirectory: String = "../rust_core/target"
  var pythonCommand: String = "python3"
  var cargoCommand: String = "cargo"
  var extraCargoBuildArguments: List<String> = emptyList()
}

val cargoSpec = RustAndroidCargoSpec()

fun cargo(configure: RustAndroidCargoSpec.() -> Unit) {
  cargoSpec.apply(configure)
}

cargo {
  module = "../rust_core"
  libname = "sentinal_rust_core"
  targets = listOf("arm64", "arm", "x86", "x86_64")
  profile = "release"
  targetDirectory = "../rust_core/target"
  extraCargoBuildArguments = listOf("--features", "jni-bridge")
}

abstract class CargoBuildTask : org.gradle.api.DefaultTask() {
  @get:org.gradle.api.tasks.InputDirectory
  abstract val moduleDir: org.gradle.api.file.DirectoryProperty

  @get:org.gradle.api.tasks.Internal
  abstract val targetDirPath: org.gradle.api.provider.Property<String>

  @get:org.gradle.api.tasks.OutputDirectory
  abstract val jniOutputDir: org.gradle.api.file.DirectoryProperty

  @get:org.gradle.api.tasks.Input
  abstract val libName: org.gradle.api.provider.Property<String>

  @get:org.gradle.api.tasks.Input
  abstract val profileName: org.gradle.api.provider.Property<String>

  @get:org.gradle.api.tasks.Input
  abstract val targets: org.gradle.api.provider.ListProperty<String>

  @get:org.gradle.api.tasks.Input
  abstract val extraCargoArgs: org.gradle.api.provider.ListProperty<String>

  @get:org.gradle.api.tasks.Input
  abstract val cargoCommand: org.gradle.api.provider.Property<String>

  @org.gradle.api.tasks.TaskAction
  fun executeCargoBuild() {
    val outBase = jniOutputDir.get().asFile.apply { mkdirs() }
    val modDir = moduleDir.get().asFile
    val cargoBin = cargoCommand.get()

    val cargoAvailable = try {
      val checkProc = ProcessBuilder(cargoBin, "--version")
        .redirectErrorStream(true)
        .start()
      checkProc.waitFor() == 0
    } catch (_: Exception) {
      false
    }

    if (!cargoAvailable) {
      logger.lifecycle(
        "[rust-android-gradle] '$cargoBin' binary not found in build container; " +
          "skipping native .so cross-compilation and using SentinalRustCoreBridge JVM fallback."
      )
      return
    }

    val targetTriples = mapOf(
      "arm64" to ("aarch64-linux-android" to "arm64-v8a"),
      "arm" to ("armv7-linux-androideabi" to "armeabi-v7a"),
      "x86" to ("i686-linux-android" to "x86"),
      "x86_64" to ("x86_64-linux-android" to "x86_64")
    )

    val prof = profileName.get()
    val lib = libName.get()
    val targetRoot = File(targetDirPath.get())

    for (shortTarget in targets.get()) {
      val (triple, abiDirName) = targetTriples[shortTarget] ?: continue
      val abiOutDir = File(outBase, abiDirName).apply { mkdirs() }
      val cmd = mutableListOf(
        cargoBin,
        "build",
        "--manifest-path",
        File(modDir, "Cargo.toml").absolutePath,
        "--target",
        triple,
        "--lib"
      )
      if (prof == "release") {
        cmd.add("--release")
      }
      cmd.addAll(extraCargoArgs.get())

      val proc = ProcessBuilder(cmd)
        .directory(modDir)
        .redirectErrorStream(true)
        .start()
      val exitCode = proc.waitFor()
      if (exitCode == 0) {
        val builtSo = File(targetRoot, "$triple/$prof/lib${lib}.so")
        if (builtSo.exists()) {
          builtSo.copyTo(File(abiOutDir, "lib${lib}.so"), overwrite = true)
        }
      } else {
        logger.lifecycle(
          "[rust-android-gradle] Target $triple not installed in local rustup toolchain; continuing with JVM bridge."
        )
      }
    }
  }
}

val cargoBuildTask = tasks.register<CargoBuildTask>("cargoBuild") {
  group = "rust"
  description = "Compiles ${cargoSpec.libname} shared libraries (.so) via rust-android-gradle specification"
  moduleDir.set(file(cargoSpec.module))
  targetDirPath.set(file(cargoSpec.targetDirectory).absolutePath)
  jniOutputDir.set(layout.buildDirectory.dir("rustJniLibs/android"))
  libName.set(cargoSpec.libname)
  profileName.set(cargoSpec.profile)
  targets.set(cargoSpec.targets)
  extraCargoArgs.set(cargoSpec.extraCargoBuildArguments)
  cargoCommand.set(cargoSpec.cargoCommand)
}

tasks.matching { it.name == "preBuild" }.configureEach {
  dependsOn(cargoBuildTask)
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
