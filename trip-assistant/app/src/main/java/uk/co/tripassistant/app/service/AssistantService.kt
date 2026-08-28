package uk.co.tripassistant.app.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.tripassistant.app.capture.ScreenCapturer
import uk.co.tripassistant.app.data.billing.EntitlementRepository
import uk.co.tripassistant.app.data.prefs.OverlaySide
import uk.co.tripassistant.app.data.prefs.OverlaySize
import uk.co.tripassistant.app.data.prefs.AppSettings
import uk.co.tripassistant.app.data.prefs.SettingsRepository
import uk.co.tripassistant.app.data.repository.ProfileRepository
import uk.co.tripassistant.app.ocr.OnDeviceTextRecognizer
import uk.co.tripassistant.app.overlay.OverlayController
import uk.co.tripassistant.app.overlay.OverlayFeedback
import uk.co.tripassistant.app.overlay.OverlayState
import uk.co.tripassistant.app.pipeline.LiveOfferPipeline
import javax.inject.Inject

/**
 * The assistant itself (spec sections 9, 36 and 37).
 *
 * The order of operations in [beginCapture] is not incidental. From Android 14 the foreground
 * service must already be running with the mediaProjection type before the projection token is
 * redeemed, and a [MediaProjection.Callback] must be registered before a virtual display is
 * created. Getting either wrong throws at runtime on a modern device.
 *
 * The service also owns teardown. A leaked VirtualDisplay leaves the capture notification up and
 * keeps draining the battery, so [stopEverything] runs on every exit path — the driver stopping
 * it, Android revoking the projection, entitlement lapsing, or the process being torn down
 * (spec sections 37, 49 and 53).
 *
 * No frame is ever written to disk and no frame ever leaves the device (spec sections 39 and 40).
 */
@AndroidEntryPoint
class AssistantService : LifecycleService() {

    @Inject lateinit var recognizer: OnDeviceTextRecognizer
    @Inject lateinit var pipeline: LiveOfferPipeline
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var profiles: ProfileRepository
    @Inject lateinit var entitlement: EntitlementRepository
    @Inject lateinit var assistantState: AssistantStateHolder
    @Inject lateinit var feedback: OverlayFeedback

    private var projection: MediaProjection? = null
    private var capturer: ScreenCapturer? = null
    private var overlay: OverlayController? = null
    private var analysisJob: Job? = null
    private var entitlementJob: Job? = null
    private var currentSettings: AppSettings = AppSettings()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * A window context bound to the default display: the correct way for a service to place an
     * overlay window and to ask how big the screen actually is on Android 11 and above.
     */
    private val overlayContext: Context by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null) {
                createDisplayContext(display)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            } else {
                this
            }
        } else {
            this
        }
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // Android ended the projection — the driver revoked it, or another app took over.
            stopEverything(AssistantStoppedReason.PROJECTION_REVOKED)
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            capturer?.resize(width, height, displayDensityDpi())
            mainHandler.post { overlay?.clampIntoScreen() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything(AssistantStoppedReason.STOPPED_BY_DRIVER)
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtraCompat(EXTRA_RESULT_DATA)
                if (resultCode != Activity.RESULT_OK || data == null) {
                    stopEverything(AssistantStoppedReason.PROJECTION_REVOKED)
                    return START_NOT_STICKY
                }
                startForegroundNotice()
                assistantState.setStatus(AssistantStatus.Starting)
                lifecycleScope.launch { beginCapture(resultCode, data) }
            }
        }

        // Deliberately not sticky: a restarted service would have no projection token, and
        // Android 14 requires fresh consent for every capture session (spec section 9).
        return START_NOT_STICKY
    }

    private fun startForegroundNotice() {
        AssistantNotifications.ensureChannel(this)
        val notification = AssistantNotifications.build(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AssistantNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(AssistantNotifications.NOTIFICATION_ID, notification)
        }
    }

    private suspend fun beginCapture(resultCode: Int, data: Intent) {
        // Entitlement is checked here, not in the UI, so there is no path that starts capture
        // without it (spec sections 3 and 5).
        if (!entitlement.currentAccess().isLive) {
            stopEverything(AssistantStoppedReason.ENTITLEMENT_REQUIRED)
            return
        }

        currentSettings = settings.current()
        pipeline.reset()
        pipeline.activeProfile = profiles.activeProfile()

        val manager = getSystemService(MediaProjectionManager::class.java)
        val newProjection = runCatching { manager?.getMediaProjection(resultCode, data) }.getOrNull()
        if (newProjection == null) {
            stopEverything(AssistantStoppedReason.PROJECTION_REVOKED)
            return
        }
        projection = newProjection

        // Must happen before a virtual display exists (Android 14 requirement).
        newProjection.registerCallback(projectionCallback, mainHandler)

        val (width, height) = displaySize()
        val newCapturer = ScreenCapturer(newProjection)
        capturer = newCapturer
        newCapturer.start(width, height, displayDensityDpi())

        withContext(Dispatchers.Main) { attachOverlay() }

        assistantState.setStatus(AssistantStatus.Running)
        observeProfileChanges()
        observeSettingsChanges()
        startEntitlementWatch()
        startAnalysisLoop(newCapturer)
    }

    private fun attachOverlay() {
        val controller = OverlayController(overlayContext) { x, y ->
            lifecycleScope.launch { settings.setOverlayPosition(x, y) }
        }
        if (!controller.canDraw()) {
            stopEverything(AssistantStoppedReason.OVERLAY_PERMISSION_LOST)
            return
        }
        controller.attach(
            savedX = currentSettings.overlayX.takeIf { it != AppSettings.OVERLAY_POSITION_UNSET },
            savedY = currentSettings.overlayY.takeIf { it != AppSettings.OVERLAY_POSITION_UNSET },
            compact = currentSettings.overlaySize == OverlaySize.COMPACT,
            preferRightSide = currentSettings.overlaySide == OverlaySide.RIGHT
        )
        controller.render(OverlayState.waiting(pipeline.activeProfile.name))
        overlay = controller
    }

    /**
     * Frames arrive already throttled and change-filtered. Recognition and analysis run here, one
     * frame at a time — the capture channel is conflated, so a slow frame is simply replaced by
     * the next one rather than building a backlog (spec section 12).
     */
    private fun startAnalysisLoop(capturer: ScreenCapturer) {
        analysisJob?.cancel()
        analysisJob = lifecycleScope.launch(Dispatchers.Default) {
            capturer.frames.collect { bitmap ->
                try {
                    val bounds = withContext(Dispatchers.Main) { overlay?.overlayBounds() }
                    val text = recognizer.recognize(bitmap)
                    val overlayState = pipeline.analyse(
                        text = text,
                        overlayBounds = bounds,
                        now = System.currentTimeMillis()
                    )
                    if (overlayState != null) {
                        withContext(Dispatchers.Main) { overlay?.render(overlayState) }
                    }
                } finally {
                    // The frame's life ends here. Nothing is stored (spec section 39).
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
        }
    }

    private fun observeProfileChanges() {
        lifecycleScope.launch {
            profiles.observeActiveProfile().collect { profile ->
                if (profile != null) pipeline.activeProfile = profile
            }
        }
    }

    private fun observeSettingsChanges() {
        lifecycleScope.launch {
            settings.settings.collect { updated ->
                val sizeChanged = updated.overlaySize != currentSettings.overlaySize
                currentSettings = updated
                if (sizeChanged) {
                    withContext(Dispatchers.Main) {
                        overlay?.detach()
                        attachOverlay()
                    }
                }
            }
        }
    }

    /**
     * Re-checks entitlement while running (spec section 5). A subscription that lapses mid-shift
     * stops live evaluation; it never touches the driver's settings or history.
     */
    private fun startEntitlementWatch() {
        entitlementJob?.cancel()
        entitlementJob = lifecycleScope.launch {
            while (true) {
                delay(ENTITLEMENT_CHECK_INTERVAL_MILLIS)
                val access = entitlement.currentAccess()
                if (access.shouldReverify) runCatching { entitlement.refresh() }
                if (!entitlement.currentAccess().isLive) {
                    stopEverything(AssistantStoppedReason.ENTITLEMENT_REQUIRED)
                    return@launch
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val (width, height) = displaySize()
        capturer?.resize(width, height, displayDensityDpi())
        mainHandler.post { overlay?.clampIntoScreen() }
    }

    private fun stopEverything(reason: AssistantStoppedReason) {
        analysisJob?.cancel()
        analysisJob = null
        entitlementJob?.cancel()
        entitlementJob = null

        capturer?.release()
        capturer = null

        projection?.let {
            runCatching { it.unregisterCallback(projectionCallback) }
            runCatching { it.stop() }
        }
        projection = null

        mainHandler.post { overlay?.detach() }
        overlay = null

        assistantState.setStatus(AssistantStatus.Stopped(reason))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Belt and braces: onDestroy can arrive without a stop action, for example when the
        // process is being reclaimed.
        analysisJob?.cancel()
        entitlementJob?.cancel()
        capturer?.release()
        capturer = null
        projection?.let {
            runCatching { it.unregisterCallback(projectionCallback) }
            runCatching { it.stop() }
        }
        projection = null
        overlay?.detach()
        overlay = null
        feedback.release()
        if (assistantState.isRunning) {
            assistantState.setStatus(AssistantStatus.Stopped(AssistantStoppedReason.STOPPED_BY_DRIVER))
        }
        super.onDestroy()
    }

    private fun displaySize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = overlayContext.getSystemService(WindowManager::class.java)
                ?.currentWindowMetrics?.bounds
            (bounds?.width() ?: 0) to (bounds?.height() ?: 0)
        } else {
            @Suppress("DEPRECATION")
            val display = getSystemService(WindowManager::class.java)?.defaultDisplay
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display?.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }

    private fun displayDensityDpi(): Int = resources.displayMetrics.densityDpi

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(name: String): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, Intent::class.java)
        } else {
            getParcelableExtra(name)
        }

    companion object {
        const val ACTION_START = "uk.co.tripassistant.app.action.START"
        const val ACTION_STOP = "uk.co.tripassistant.app.action.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        private const val ENTITLEMENT_CHECK_INTERVAL_MILLIS = 15L * 60L * 1000L

        /** Built from the result of Android's own capture consent dialog — never a stored token. */
        fun startIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, AssistantService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, AssistantService::class.java).setAction(ACTION_STOP)
    }
}
