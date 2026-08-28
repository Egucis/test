package uk.co.tripassistant.app.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * MediaProjection capture (spec sections 9 and 12).
 *
 * Responsibilities, and their reasons:
 *  * a [VirtualDisplay] plus an [ImageReader], both sized *down* from the real display, because
 *    OCR does not need 1440p and a shift-long capture at full resolution is a battery problem;
 *  * a dedicated handler thread, so frame delivery never touches the main thread;
 *  * a throttle and a [FrameChangeDetector] in front of every allocation, so a still screen costs
 *    almost nothing (spec section 12);
 *  * a conflated channel out, so if recognition is still busy the newest frame replaces the
 *    queued one instead of building a backlog;
 *  * [release] that actually tears everything down — a leaked VirtualDisplay keeps the capture
 *    notification alive and drains the battery (spec section 53).
 *
 * This class never persists a frame. Bitmaps live only as long as the analysis that consumes them
 * (spec sections 39 and 40).
 */
class ScreenCapturer(
    private val projection: MediaProjection,
    /** Roughly 3 analyses a second when the screen is moving (spec section 12). */
    private val minFrameIntervalMillis: Long = 280L,
    private val maxCaptureEdge: Int = 1440
) {

    // Capacity of one with DROP_OLDEST: while recognition is busy, the queued frame is replaced
    // rather than queued behind. onUndeliveredElement frees the frame that was dropped — bitmaps
    // are large enough that waiting for the collector would show up over a long shift
    // (spec section 53).
    private val frameChannel = Channel<Bitmap>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { dropped -> if (!dropped.isRecycled) dropped.recycle() }
    )
    private val changeDetector = FrameChangeDetector()

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var lastAcceptedAt = 0L
    private var currentSize: CaptureSize? = null
    private var released = false

    /** Frames worth recognising. Conflated: a slow consumer sees the newest frame, not a queue. */
    val frames: Flow<Bitmap> = frameChannel.receiveAsFlow()

    data class CaptureSize(val width: Int, val height: Int, val densityDpi: Int)

    @Synchronized
    fun start(displayWidth: Int, displayHeight: Int, densityDpi: Int) {
        if (released) return
        val thread = HandlerThread("trip-assistant-capture").also { it.start() }
        handlerThread = thread
        handler = Handler(thread.looper)
        configure(displayWidth, displayHeight, densityDpi)
    }

    /**
     * Rebuilds the reader and display at a new size. Called on rotation, on a display resize, and
     * on Android 14's captured-content resize callback (spec section 9).
     */
    @Synchronized
    fun resize(displayWidth: Int, displayHeight: Int, densityDpi: Int) {
        if (released || handler == null) return
        val target = scaled(displayWidth, displayHeight, densityDpi)
        if (target == currentSize) return
        configure(displayWidth, displayHeight, densityDpi)
    }

    private fun configure(displayWidth: Int, displayHeight: Int, densityDpi: Int) {
        val size = scaled(displayWidth, displayHeight, densityDpi)
        if (size.width <= 0 || size.height <= 0) return

        releaseDisplaySurfaces()
        changeDetector.reset()

        val reader = ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, MAX_IMAGES)
        reader.setOnImageAvailableListener({ onImageAvailable(it) }, handler)
        imageReader = reader

        virtualDisplay = runCatching {
            projection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                size.width,
                size.height,
                size.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )
        }.getOrElse {
            Log.w(TAG, "Could not create the virtual display")
            reader.close()
            imageReader = null
            null
        }

        if (virtualDisplay != null) currentSize = size
    }

    private fun onImageAvailable(reader: ImageReader) {
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAcceptedAt < minFrameIntervalMillis) return

            val plane = image.planes.firstOrNull() ?: return
            val changed = changeDetector.hasChanged(
                buffer = plane.buffer,
                width = image.width,
                height = image.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride
            )
            if (!changed) return

            lastAcceptedAt = now
            val bitmap = ImageConverter.toBitmap(image) ?: return
            // Conflated: if the previous frame is still being recognised it is simply replaced.
            val delivered = frameChannel.trySend(bitmap)
            if (delivered.isFailure) bitmap.recycle()
        } catch (error: Exception) {
            // A frame is disposable. Never let one bad frame end a shift.
            Log.w(TAG, "Skipped a frame: ${error.javaClass.simpleName}")
        } finally {
            runCatching { image.close() }
        }
    }

    /** Scales the display down so the long edge is at most [maxCaptureEdge]. */
    private fun scaled(width: Int, height: Int, densityDpi: Int): CaptureSize {
        val longest = max(width, height)
        if (longest <= maxCaptureEdge || longest == 0) return CaptureSize(width, height, densityDpi)
        val factor = maxCaptureEdge.toDouble() / longest
        return CaptureSize(
            width = (width * factor).roundToInt().coerceAtLeast(1),
            height = (height * factor).roundToInt().coerceAtLeast(1),
            densityDpi = (densityDpi * factor).roundToInt().coerceAtLeast(1)
        )
    }

    private fun releaseDisplaySurfaces() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.setOnImageAvailableListener(null, null) }
        runCatching { imageReader?.close() }
        imageReader = null
    }

    @Synchronized
    fun release() {
        if (released) return
        released = true
        releaseDisplaySurfaces()
        frameChannel.close()
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        currentSize = null
    }

    private companion object {
        const val TAG = "ScreenCapturer"
        const val VIRTUAL_DISPLAY_NAME = "TripAssistantCapture"

        /** Two buffers: one being filled, one being read. More would only add latency. */
        const val MAX_IMAGES = 2
    }
}
