import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.voicerecorder.voxrec"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

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
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  lint {
    abortOnError = false
    checkReleaseBuilds = false
    baseline = file("lint-baseline.xml")
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Base directory for all Roborazzi captures (AI feed lives inside it).
roborazzi {
  outputDir.set(layout.buildDirectory.dir("outputs/roborazzi"))
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // firebase-bom removed in Phase 4 — not used (was placeholder for firebase-ai, now direct Gemini via OkHttp)
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
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
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

// Prepares Roborazzi captures for vision-capable AI agents:
// 1. Runs the screenshot recording task first.
// 2. Down-scales every PNG to 50% (fewer vision tokens) with high-quality
//    filtering and re-encodes it as JPG.
// 3. Mirrors the ai_feed folder structure into build/outputs/ai_feed.
// 4. Emits images_manifest.json describing every generated image.
tasks.register("prepareImagesForAI") {
  group = "roborazzi"
  description = "Downscale/compress Roborazzi captures into an AI-friendly feed with a JSON manifest"
  dependsOn("recordRoborazziDebug")
  // Phase 4: configuration-cache compatible inputs/outputs
  val inputDirProvider = layout.buildDirectory.dir("outputs/roborazzi")
  val outputDirProvider = layout.buildDirectory.dir("outputs/ai_feed")
  inputs.dir(inputDirProvider)
  outputs.dir(outputDirProvider)

  doLast {
    val inputDir = inputDirProvider.get().asFile
    val outputDir = outputDirProvider.get().asFile
    outputDir.mkdirs()

    if (!inputDir.exists()) {
      println("No Roborazzi input found at ${inputDir.absolutePath}")
      return@doLast
    }

    val scaleFactor = 0.5
    var processedCount = 0

    inputDir.walkTopDown()
      .filter {
        it.isFile &&
          it.extension.equals("png", ignoreCase = true) &&
          !it.name.contains("_compare") &&
          !it.name.contains("_group")
      }
      .forEach { file ->
        try {
          val original: BufferedImage = ImageIO.read(file) ?: return@forEach
          val targetWidth = (original.width * scaleFactor).toInt().coerceAtLeast(1)
          val targetHeight = (original.height * scaleFactor).toInt().coerceAtLeast(1)

          val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
          val g2d: Graphics2D = resized.createGraphics()
          g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
          g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
          g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
          g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null)
          g2d.dispose()

          // Preserve the ai_feed sub-structure: <group>/<name>.jpg
          val relativeParent = file.relativeTo(inputDir).parent ?: ""
          val outputFile = File(File(outputDir, relativeParent), "${file.nameWithoutExtension}.jpg")
          outputFile.parentFile.mkdirs()
          ImageIO.write(resized, "jpg", outputFile)

          processedCount++
          println("AI feed: ${outputFile.relativeTo(outputDir).path} (${targetWidth}x${targetHeight})")
        } catch (e: Exception) {
          println("Failed to process ${file.name}: ${e.message}")
        }
      }

    val manifestFile = File(outputDir, "images_manifest.json")
    val jsonContent = outputDir.walkTopDown()
      .filter { it.isFile && it.extension == "jpg" }
      .toList()
      .joinToString(
        prefix = "[\n",
        postfix = "\n]",
        separator = ",\n"
      ) { f ->
        """  { "file_name": "${f.relativeTo(outputDir).path}", "size_kb": ${f.length() / 1024} }"""
      }
    manifestFile.writeText(jsonContent)

    println("AI feed ready: $processedCount image(s) in ${outputDir.absolutePath}")
    println("Manifest: ${manifestFile.relativeTo(outputDir).path}")
  }
}
