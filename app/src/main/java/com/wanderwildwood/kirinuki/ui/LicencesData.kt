package com.wanderwildwood.kirinuki.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.kirinuki.R

/** The address of the app's own source code. */
const val SOURCE_URL = "https://github.com/wanderwildwood/kirinuki"

/**
 * Everything the app is built on, as the build pulls it in. Licences are the ones each
 * project declares in its own published metadata.
 */
val LICENCE_COMPONENTS: List<LicenceComponent> = listOf(
    LicenceComponent("Feeder", "Jonas Kalderstam", "GPL-3.0", "https://gitlab.com/spacecowboy/Feeder"),
    LicenceComponent("Read You (parts of the Google Reader sync)", "Ashinch and contributors", "GPL-3.0", "https://github.com/Ashinch/ReadYou"),
    LicenceComponent("Mudita Mindful Design (MMD)", "Mudita", "Apache-2.0", "https://github.com/mudita/MMD"),
    LicenceComponent("AndroidX: Core, AppCompat, Lifecycle, Navigation, Room, Paging, Preference, Browser, Emoji2, Activity", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
    LicenceComponent("Jetpack Compose and Glance", "Google", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
    LicenceComponent("Media3 (ExoPlayer, Session)", "Google", "Apache-2.0", "https://developer.android.com/media/media3"),
    LicenceComponent("Material Components for Android", "Google", "Apache-2.0", "https://github.com/material-components/material-components-android"),
    LicenceComponent("Accompanist", "Google", "Apache-2.0", "https://github.com/google/accompanist"),
    LicenceComponent("Kotlin standard library, kotlinx.coroutines, kotlinx.serialization", "JetBrains", "Apache-2.0", "https://kotlinlang.org"),
    LicenceComponent("Markdown", "JetBrains", "Apache-2.0", "https://github.com/JetBrains/markdown"),
    LicenceComponent("OkHttp and Okio", "Square", "Apache-2.0", "https://square.github.io/okhttp/"),
    LicenceComponent("Retrofit", "Square", "Apache-2.0", "https://square.github.io/retrofit/"),
    LicenceComponent("Moshi", "Square", "Apache-2.0", "https://github.com/square/moshi"),
    LicenceComponent("Coil", "Coil contributors", "Apache-2.0", "https://coil-kt.github.io/coil/"),
    LicenceComponent("Coil ICO decoder", "Daniel Rampelt", "Apache-2.0", "https://github.com/drampelt/coil-ico"),
    LicenceComponent("Kodein-DI", "Kodein Koders", "MIT", "https://github.com/kosi-libs/Kodein"),
    LicenceComponent("jsoup", "Jonathan Hedley", "MIT", "https://jsoup.org"),
    LicenceComponent("TagSoup", "John Cowan", "Apache-2.0", "https://central.sonatype.com/artifact/org.ccil.cowan.tagsoup/tagsoup"),
    LicenceComponent("Readability4J", "dankito", "Apache-2.0", "https://github.com/dankito/Readability4J"),
    LicenceComponent("gofeed-android", "Jonas Kalderstam", "MIT", "https://central.sonatype.com/artifact/com.nononsenseapps.gofeed/gofeed-android"),
    LicenceComponent("ICU4J", "Unicode, Inc.", "Unicode-3.0", "https://icu.unicode.org"),
    LicenceComponent("QRGen and ZXing", "Ken Glxn, ZXing authors", "Apache-2.0", "https://github.com/kenglxn/QRGen"),
    LicenceComponent("LazyColumnScrollbar", "nani", "MIT", "https://github.com/nanihadesuka/LazyColumnScrollbar"),
    LicenceComponent("Java library desugaring (desugar_jdk_libs)", "Google", "GPL-2.0 with Classpath Exception", "https://github.com/google/desugar_jdk_libs"),
    LicenceComponent("Bergamot translator (bundled web files)", "Mozilla and contributors", "MPL-2.0", "https://github.com/browsermt/bergamot-translator"),
)

/** Each distinct licence text once: family id and the raw resource. */
val LICENCE_RAW: List<Pair<String, Int>> = listOf(
    "gpl" to R.raw.licence_gpl3,
    "gpl2-cpe" to R.raw.licence_gpl2_cpe,
    "apache" to R.raw.licence_apache2,
    "mpl" to R.raw.licence_mpl2,
    "mit" to R.raw.licence_mit,
    "unicode" to R.raw.licence_unicode,
)

@Composable
fun rememberLicencesData(): LicencesData = LicencesData(
    appName = "Feeds",
    sourceUrl = SOURCE_URL,
    licenceLine = stringResource(R.string.licences_licence),
    components = LICENCE_COMPONENTS,
    texts = LICENCE_RAW.map { (id, raw) ->
        LicenceText(id, stringResource(when (id) {
            "gpl" -> R.string.licences_full_gpl
            "gpl2-cpe" -> R.string.licences_full_gpl2_cpe
            "apache" -> R.string.licences_full_apache
            "mpl" -> R.string.licences_full_mpl
            "mit" -> R.string.licences_full_mit
            "unicode" -> R.string.licences_full_unicode
            else -> R.string.licences_full_head
        }), raw)
    },
)

@Composable
fun rememberLicencesLabels(): LicencesLabels {
    val none = stringResource(R.string.about_no_browser)
    val noText = stringResource(R.string.licences_no_text, "@@")
    return LicencesLabels(
        title = stringResource(R.string.licences_title),
        back = stringResource(R.string.licences_back),
        sourceHeading = stringResource(R.string.licences_source_head),
        noText = { licence -> noText.replace("@@", licence) },
        noBrowser = { _ -> none },
    )
}
