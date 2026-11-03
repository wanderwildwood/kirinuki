package com.wanderwildwood.kirinuki.ui.qr

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.net.scannedAddress
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon
import com.wanderwildwood.kirinuki.ui.compose.theme.HouseButton
import com.wanderwildwood.kirinuki.ui.compose.theme.Icons
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Looks through the camera for a QR code holding a feed's or a site's address, made on a
 * computer for a phone that has no browser to find it with. The camera is asked for only here,
 * after the reader has chosen to scan, and it is held only while this screen is open: nothing
 * it sees is kept or sent, and the frames are read on the phone with ZXing.
 *
 * Whatever goes wrong -- no camera, the camera refused, the camera busy, a code that is not an
 * address -- is said in words on this screen, and the way out is the same Back as always.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var allowed by remember { mutableStateOf(context.cameraAllowed()) }
    var refused by remember { mutableStateOf(false) }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            allowed = granted
            refused = !granted
        }
    // Back from the phone's settings with the camera allowed there: go straight to looking.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = context.cameraAllowed() }

    BackHandler(onBack = onClose)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.qr_scan_title)) },
                navigationIcon = {
                    BarIcon(
                        icon = Icons.Back,
                        contentDescription = stringResource(R.string.go_back),
                        onClick = onClose,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            when {
                !hasCamera ->
                    TextMMD(
                        text = stringResource(R.string.qr_no_camera),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                !allowed -> {
                    TextMMD(
                        text = stringResource(if (refused) R.string.qr_camera_refused else R.string.qr_camera_why),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HouseButton(
                        label = stringResource(R.string.qr_camera_allow),
                        onClick = { ask.launch(Manifest.permission.CAMERA) },
                        on = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Refused twice, Android stops asking and the press above does nothing;
                    // the phone's own page for the app is then the only place to say yes.
                    if (refused) {
                        HouseButton(
                            label = stringResource(R.string.qr_camera_settings),
                            onClick = { context.openAppSettings() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                else -> CameraLook(onScanned = onScanned, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun CameraLook(
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val notAddress = stringResource(R.string.qr_not_address)
    val noOpen = stringResource(R.string.qr_camera_failed)
    var note by remember { mutableStateOf<String?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val done = remember { AtomicBoolean(false) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // The last refused code, so a code held still is not re-read and re-refused every frame.
    var lastRefused by remember { mutableStateOf<String?>(null) }

    TextMMD(
        text = note ?: stringResource(R.string.qr_scan_hold),
        style = MaterialTheme.typography.bodyMedium,
    )
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view = PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
            val future =
                try {
                    ProcessCameraProvider.getInstance(ctx)
                } catch (e: Exception) {
                    note = noOpen
                    return@AndroidView view
                }
            future.addListener({
                val ready = runCatching { future.get() }.getOrNull()
                if (ready == null) {
                    note = noOpen
                    return@addListener
                }
                provider = ready
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis =
                    ImageAnalysis
                        .Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                analysis.setAnalyzer(executor) { image ->
                    val text = if (done.get()) null else read(image)
                    if (text != null) {
                        view.post {
                            if (done.get()) return@post
                            val address = scannedAddress(text)
                            if (address != null) {
                                done.set(true)
                                currentOnScanned(address)
                            } else if (text != lastRefused) {
                                lastRefused = text
                                note = notAddress
                            }
                        }
                    }
                }
                try {
                    ready.unbindAll()
                    val selector =
                        if (ready.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        }
                    ready.bindToLifecycle(lifecycle, selector, preview, analysis)
                } catch (e: Exception) {
                    // No usable camera after all, or another app holds it.
                    note = noOpen
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
    )
    DisposableEffect(Unit) {
        onDispose {
            // The camera is let go the moment the scanner closes, not when the app does.
            runCatching { provider?.unbindAll() }
            executor.shutdown()
        }
    }
}

/** The brightness plane of a frame, read for a code; the frame is always handed back. */
private fun read(image: ImageProxy): String? =
    try {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
        QrCodes.decode(data, plane.rowStride, image.width, image.height)
    } catch (e: Exception) {
        null
    } finally {
        image.close()
    }

private fun Context.cameraAllowed(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    try {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: ActivityNotFoundException) {
        // Nothing to open it with; the words on the screen already say where to go.
    }
}
