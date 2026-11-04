package com.wanderwildwood.kirinuki.ui.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.kirinuki.BuildConfig
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.ui.AboutData
import com.wanderwildwood.kirinuki.ui.AboutLabels
import com.wanderwildwood.kirinuki.ui.AboutSection
import com.wanderwildwood.kirinuki.ui.HouseAboutDialog
import com.wanderwildwood.kirinuki.ui.LicencesScreen
import com.wanderwildwood.kirinuki.ui.SOURCE_URL
import com.wanderwildwood.kirinuki.ui.rememberLicencesData
import com.wanderwildwood.kirinuki.ui.rememberLicencesLabels

/**
 * This app's About: only the words and the address. The dialog, its one Close button, the
 * Licences row, the links and the llama are the shared HouseAbout.kt, the same in every app.
 *
 * An app that goes to the network owes the reader that line, and this one goes to whatever
 * addresses it is given.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val data = AboutData(
        title = stringResource(R.string.about_title, BuildConfig.VERSION_NAME),
        sections = listOf(
            AboutSection(null, stringResource(R.string.about_what)),
            AboutSection(stringResource(R.string.about_privacy_head), stringResource(R.string.about_privacy)),
            AboutSection(stringResource(R.string.privacy_who_title), stringResource(R.string.privacy_who)),
            AboutSection(stringResource(R.string.privacy_contact_title), stringResource(R.string.privacy_contact)),
            AboutSection(stringResource(R.string.privacy_rights_title), stringResource(R.string.privacy_rights)),
            AboutSection(null, stringResource(R.string.about_built_on)),
            AboutSection(stringResource(R.string.about_terms_head), stringResource(R.string.about_licence)),
        ),
        sourceUrl = SOURCE_URL,
    )
    val labels = AboutLabels(
        close = stringResource(R.string.about_close),
        licences = stringResource(R.string.licences_title),
        feedTheLlamas = stringResource(R.string.about_feed_the_llamas),
        noBrowser = { _ -> context.getString(R.string.about_no_browser) },
        writeTo = { address -> context.getString(R.string.about_write_to, address) },
        noMailApp = { address -> context.getString(R.string.about_no_mail_app, address) },
    )
    HouseAboutDialog(data, labels, R.drawable.llama, onDismiss) { modifier, back ->
        LicencesScreen(rememberLicencesData(), rememberLicencesLabels(), onBack = back, modifier = modifier)
    }
}
