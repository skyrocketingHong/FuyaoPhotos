import java.util.Properties
import java.io.RandomAccessFile
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.FilterConfiguration

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appMarketingVersion = "27.0"
val appBuildTrain = "1A"
val appBuildSequenceOffset = 0
val androidVersionBase = appMarketingVersion.replace(".", "").toInt()

/** One reservation per Gradle invocation, shared by every variant in that build. */
abstract class ReserveBuildNumber : DefaultTask() {
    @get:Input
    abstract val marketingVersion: Property<String>

    @get:Input
    abstract val versionCodeBase: Property<Int>

    @get:Input
    abstract val buildTrain: Property<String>

    @get:Input
    abstract val sequenceOffset: Property<Int>

    @get:Internal
    abstract val counterFile: RegularFileProperty

    @get:OutputFile
    abstract val receiptFile: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
        outputs.doNotCacheIf("Build numbers must never be reused") { true }
    }

    @TaskAction
    fun reserve() {
        val versionBase = versionCodeBase.get()
        val offset = sequenceOffset.get()
        require(offset in 0 until 1_000_000) { "Invalid build sequence baseline" }
        // ASVS 15.4.1/15.4.2: read and increment under the same cross-process lock.
        val next = RandomAccessFile(counterFile.get().asFile, "rw").use { counter ->
            counter.channel.lock().use {
                val previous = if (counter.length() == 0L) offset else {
                    // ASVS 2.2.1: reject a damaged counter instead of resetting it.
                    require(counter.length() <= 16L) { "Invalid build counter" }
                    counter.readLine().trim().toInt()
                }
                require(previous in offset until 1_000_000) {
                    "Build counter precedes the train baseline or exceeds the Android versionCode range"
                }
                val value = previous + 1
                counter.seek(0)
                counter.writeBytes("$value\n")
                counter.setLength(counter.filePointer)
                counter.fd.sync()
                value
            }
        }
        val sequence = next - offset
        receiptFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(sequence.toString())
        }
        logger.lifecycle(
            "Fuyao Photo Info ${marketingVersion.get()} (${buildTrain.get()}$sequence), " +
                "versionCode=$versionBase${"%03d".format(sequence)}"
        )
    }
}

val reserveBuildNumber = tasks.register<ReserveBuildNumber>("reserveBuildNumber") {
    marketingVersion.set(appMarketingVersion)
    versionCodeBase.set(androidVersionBase)
    buildTrain.set(appBuildTrain)
    sequenceOffset.set(appBuildSequenceOffset)
    counterFile.set(rootProject.layout.projectDirectory.file(".build-counter"))
    receiptFile.set(layout.buildDirectory.file("intermediates/build-number/sequence.txt"))
}
val buildSequence = reserveBuildNumber.map { it.receiptFile.get().asFile.readText().trim().toInt() }

val signing = Properties().apply {
    rootProject.file("signing.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val privateSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !signing.getProperty(it).isNullOrBlank() }

android {
    namespace = "ing.fuyaoskyrocket.photoinfo"
    compileSdk = 37
    defaultConfig {
        applicationId = "ing.fuyaoskyrocket.photoinfo"
        minSdk = 26
        targetSdk = 37
        versionCode = "$androidVersionBase${"%03d".format(1)}".toInt()
        versionName = "${appBuildTrain}1"
        buildConfigField("String", "MARKETING_VERSION", "\"$appMarketingVersion\"")
        manifestPlaceholders["appLabel"] = "Fuyao Photo Info"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    if (privateSigning) {
        signingConfigs.create("privateRelease") {
            storeFile = rootProject.file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    // Local release builds match the other Fuyao apps; never label this fallback as a private release signature.
    signingConfigs.getByName("debug") {
        enableV1Signing = true
        enableV2Signing = true
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Fuyao Photo Info Debug"
        }
        create("debugUnsigned") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".debug.unsigned"
            versionNameSuffix = "-debug-unsigned"
            signingConfig = null
            manifestPlaceholders["appLabel"] = "Fuyao Photo Info Debug Unsigned"
            matchingFallbacks += "debug"
        }
        release {
            // Prefer the private key; otherwise provide an explicitly documented, locally signed Release.
            signingConfig = if (privateSigning) signingConfigs.getByName("privateRelease") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("releaseUnsigned") {
            initWith(getByName("release"))
            applicationIdSuffix = ".unsigned"
            versionNameSuffix = "-unsigned"
            signingConfig = null
            manifestPlaceholders["appLabel"] = "Fuyao Photo Info Release Unsigned"
            matchingFallbacks += "release"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents.onVariants { variant ->
    val apkAppName = rootProject.name
    val marketingVersion = appMarketingVersion
    val variantName = variant.name
    val packageAndBuild = variant.applicationId.zip(buildSequence) { packageName, sequence ->
        packageName to "$appBuildTrain$sequence"
    }
    val suffix = when (variant.buildType) {
        "debug" -> "-debug"
        "debugUnsigned" -> "-debug-unsigned"
        "releaseUnsigned" -> "-unsigned"
        else -> ""
    }
    variant.outputs.forEach { output ->
        output.versionCode.set(buildSequence.map { sequence ->
            "$androidVersionBase${"%03d".format(sequence)}".toInt()
        })
        output.versionName.set(buildSequence.map { "$appBuildTrain$it$suffix" })
        // Public AGP API: the packager and output-metadata.json agree on the
        // filename. Providers keep the build counter out of configuration/help.
        val abi = output.filters.firstOrNull {
            it.filterType == FilterConfiguration.FilterType.ABI
        }?.identifier ?: "universal"
        val extraFilters = output.filters
            .filterNot { it.filterType == FilterConfiguration.FilterType.ABI }
            .joinToString("") { "-${it.filterType.name.lowercase()}-${it.identifier}" }
        output.outputFileName.set(packageAndBuild.map { identity ->
            "${apkAppName}-${identity.first}-${marketingVersion}(${identity.second})" +
                "-${abi}${extraFilters}-${variantName}.apk"
        })
    }
    checkNotNull(variant.buildConfigFields).put("BUILD_NUMBER", buildSequence.map {
        BuildConfigField("String", "\"$appBuildTrain$it\"", "Build train and invocation sequence since baseline")
    })
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
