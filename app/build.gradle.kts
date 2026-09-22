import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint.gradle)
}

val kotlinToolchainVersion =
    JavaVersion
        .current()
        .majorVersion
        .toIntOrNull()
        ?.takeIf { JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) }
        ?.coerceAtLeast(17)
        ?: 17

android {
    namespace = "com.wanderwildwood.kirinuki"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.wanderwildwood.kirinuki"
        versionCode = 11
        versionName = "0.3.3"
        // TLS1.3 is enabled in Android 10 (29) and above
        minSdk = 29
        targetSdk =
            libs.versions.compileSdk
                .get()
                .toInt()

        vectorDrawables.useSupportLibrary = true

        androidResources.localeFilters.addAll(getListOfSupportedLocales())

        // For espresso tests
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The Go feed parser ships a native library per ABI, and all of them together
        // make a 140 MB debug APK that an emulator struggles to install and run.
        // Pass -PabiFilter=x86_64 for an emulator build; release keeps every ABI.
        (project.findProperty("abiFilter") as String?)?.let { abi ->
            ndk {
                abiFilters.add(abi)
            }
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs (not wanted for reproducible builds)
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles
        includeInBundle = false
    }

    ksp {
        arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
    }

    sourceSets {
        // To test Room we need to include the schema dir in resources
        named("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }

    // A real keystore in signing/ signs every build type when it is present, so the
    // very first install is already release-signed and a later update can never hit
    // INSTALL_FAILED_UPDATE_INCOMPATIBLE. It is gitignored, and there is no fallback:
    // a fresh clone builds an unsigned release APK, which will not install anywhere.
    // A keystore committed to a public repo is not a signing key, it is a formality,
    // and a missing one should stop you rather than produce something installable.
    // (Debug builds still get the ordinary Android debug key from AGP.)
    val signingPropertiesFile = rootProject.file("signing/signing.properties")
    val realSigningConfig =
        if (signingPropertiesFile.isFile) {
            val signingProperties =
                Properties().apply {
                    signingPropertiesFile.inputStream().use(::load)
                }
            signingConfigs.create("real") {
                storeFile = rootProject.file("signing/signing.keystore")
                storePassword = signingProperties.getProperty("STORE_PASSWORD")
                keyAlias = signingProperties.getProperty("KEY_ALIAS")
                keyPassword = signingProperties.getProperty("KEY_PASSWORD")
            }
        } else {
            null
        }

    buildTypes {
        val debug by getting {
            isMinifyEnabled = false
            isShrinkResources = false
            isPseudoLocalesEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            realSigningConfig?.let { signingConfig = it }
        }
        val release by getting {
            // AGP stamps the git revision into META-INF of a release build. This one is
            // built on the build box, which works from an rsync with no .git, so it writes
            // NO_SUPPORTED_VCS_FOUND instead -- but the same tag built here would carry the
            // commit, and two APKs of one version would differ. Off, so where it was built
            // makes no difference to what is in it.
            vcsInfo {
                include = false
            }

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            realSigningConfig?.let { signingConfig = it }
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
        managedDevices {
            allDevices {
                maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("pixel2api30").apply {
                    // Use device profiles you typically see in Android Studio.
                    device = "Pixel 2"
                    // Use only API levels 27 and higher.
                    apiLevel = 30
                    // To include Google services, use "google".
                    systemImageSource = "aosp-atd"
                    testedAbi = "x86"
                }
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
        resValues = false
        shaders = false
    }

    packaging {
        resources {
            excludes.addAll(
                listOf(
                    "META-INF/DEPENDENCIES",
                    "META-INF/LICENSE",
                    "META-INF/LICENSE.md",
                    "META-INF/LICENSE.txt",
                    "META-INF/license.txt",
                    "META-INF/LICENSE-notice.md",
                    "META-INF/NOTICE",
                    "META-INF/NOTICE.txt",
                    "META-INF/notice.txt",
                    "META-INF/ASL2.0",
                    "META-INF/AL2.0",
                    "META-INF/LGPL2.1",
                ),
            )
        }
    }

    lint {
        abortOnError = true
        disable.addAll(listOf("MissingTranslation", "AppCompatCustomView", "InvalidPackage"))
        error.addAll(listOf("InlinedApi", "StringEscaping"))
        explainIssues = true
        ignoreWarnings = true
        textOutput = file("stdout")
        textReport = true
    }
}

composeCompiler {
    includeSourceInformation = true
    // reportsDestination = layout.buildDirectory.dir("compose_metrics")
}

kotlin {
    jvmToolchain(kotlinToolchainVersion)
    compilerOptions {
        allWarningsAsErrors = false
        jvmTarget = JvmTarget.JVM_11
    }
}

configurations.all {
    resolutionStrategy {
//    failOnVersionConflict()
    }
}

// gradle ktlint are not updating ktlint as they should
// so need to specify to make it compatible with compose rules
ktlint {
    version.set("1.5.0")
}

dependencies {
    // MMD decides type, switches, buttons and dividers -- see /opt/projects/STYLE.md.
    implementation("com.mudita:MMD:1.0.2")

    ktlintRuleset(libs.ktlint.compose)
    ksp(libs.room)
    // For java time
    coreLibraryDesugaring(libs.desugar)

    // BOMS
    implementation(platform(libs.okhttp.bom))
    implementation(platform(libs.compose.bom))
    implementation(platform(libs.openai.client.bom))
    implementation(platform(libs.retrofit.bom))

    // Dependencies
    implementation(libs.bundles.android)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.jvm)
    implementation(libs.bundles.okhttp.android)
    implementation(libs.bundles.kotlin)
    implementation(libs.openai.client)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.glance.preview)
    implementation(libs.glance.appwidget.preview)

    // Markdown
    implementation(libs.jetbrains.markdown)

    // Tests
    testImplementation(libs.bundles.kotlin)
    testImplementation(libs.bundles.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.bundles.kotlin)
    androidTestImplementation(libs.bundles.android.test)

    debugImplementation(libs.compose.ui.test.manifest)
}

fun getListOfSupportedLocales(): List<String> {
    val resFolder = file(projectDir.resolve("src/main/res"))

    return resFolder
        .list { _, s ->
            s.startsWith("values")
        }?.filter { folder ->
            val stringsSize = resFolder.resolve("$folder/strings.xml").length()
            // values/strings.xml is over 13k in size so this filters out too partial translations
            stringsSize > 10_000L
        }?.map { folder ->
            if (folder == "values") {
                "en"
            } else {
                folder.substringAfter("values-")
            }
        }?.sorted()
        ?: listOf("en")
}

tasks {
    register("generateLocalesConfig") {
        val resFolder = file(projectDir.resolve("src/main/res"))
        inputs.files(
            resFolder
                .listFiles { file ->
                    file.name.startsWith("values")
                }?.map { file ->
                    file.resolve("strings.xml")
                } ?: error("Could not resolve values folders!"),
        )

        val localesConfigFile = file(projectDir.resolve("src/main/res/xml/locales_config.xml"))
        outputs.file(projectDir.resolve("src/main/res/xml/locales_config.xml"))

        doLast {
            val langs = getListOfSupportedLocales()
            val localesConfig =
                """
                <?xml version="1.0" encoding="utf-8"?>
                <locale-config xmlns:android="http://schemas.android.com/apk/res/android">
                ${langs.joinToString(" ") { "<locale android:name=\"$it\"/>" }}
                </locale-config>
                """.trimIndent()

            localesConfigFile.bufferedWriter().use { writer ->
                writer.write(localesConfig)
            }
        }
    }
}

class RoomSchemaArgProvider(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val schemaDir: File,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        // Note: If you're using KSP, change the line below to return
        return listOf("room.schemaLocation=${schemaDir.path}")
    }
}
