package com.wanderwildwood.kirinuki.ui.licences

/** One piece of other people's work that ships inside the app, and the licence it comes under. */
data class Library(
    val name: String,
    val by: String,
    val licence: String,
)

/** The address of the app's own source code. */
const val SOURCE_URL = "https://github.com/wanderwildwood/kirinuki"

/**
 * Everything the app is built on, as the build pulls it in. Licences are the ones each
 * project declares in its own published metadata.
 */
val LIBRARIES: List<Library> =
    listOf(
        Library("Feeder", "Jonas Kalderstam", "GPL-3.0"),
        Library("Mudita Mindful Design (MMD)", "Mudita", "Apache-2.0"),
        Library("AndroidX: Core, AppCompat, Lifecycle, Navigation, Room, Paging, Preference, Browser, Emoji2, Activity", "Google", "Apache-2.0"),
        Library("Jetpack Compose and Glance", "Google", "Apache-2.0"),
        Library("Media3 (ExoPlayer, Session)", "Google", "Apache-2.0"),
        Library("Material Components for Android", "Google", "Apache-2.0"),
        Library("Accompanist", "Google", "Apache-2.0"),
        Library("Kotlin standard library, kotlinx.coroutines, kotlinx.serialization", "JetBrains", "Apache-2.0"),
        Library("Markdown", "JetBrains", "Apache-2.0"),
        Library("OkHttp and Okio", "Square", "Apache-2.0"),
        Library("Retrofit", "Square", "Apache-2.0"),
        Library("Moshi", "Square", "Apache-2.0"),
        Library("Coil", "Coil contributors", "Apache-2.0"),
        Library("Coil ICO decoder", "Daniel Rampelt", "Apache-2.0"),
        Library("Kodein-DI", "Kodein Koders", "MIT"),
        Library("jsoup", "Jonathan Hedley", "MIT"),
        Library("TagSoup", "John Cowan", "Apache-2.0"),
        Library("Readability4J", "dankito", "Apache-2.0"),
        Library("gofeed-android", "Jonas Kalderstam", "MIT"),
        Library("ICU4J", "Unicode, Inc.", "Unicode-3.0"),
        Library("QRGen and ZXing", "Ken Glxn, ZXing authors", "Apache-2.0"),
        Library("LazyColumnScrollbar", "nani", "MIT"),
        Library("Java library desugaring (desugar_jdk_libs)", "Google", "GPL-2.0 with Classpath Exception"),
        Library("Bergamot translator (bundled web files)", "Mozilla and contributors", "MPL-2.0"),
    )

/**
 * A licence text as the paragraphs a reader turns pages by. The texts are wrapped at about
 * seventy columns with a blank line between paragraphs; each paragraph is joined into one
 * row, and no word is dropped or repeated by it.
 */
fun licenceParagraphs(text: String): List<String> =
    text
        .replace("\r\n", "\n")
        .split(Regex("\n[ \t]*\n"))
        .map { block -> block.lines().joinToString(" ") { it.trim() }.trim() }
        .filter { it.isNotEmpty() }
