package uk.co.tripassistant.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.content.ContextCompat
import uk.co.tripassistant.app.R
import uk.co.tripassistant.app.util.OverlayPermission
import uk.co.tripassistant.app.databinding.OverlayAssistantBinding
import uk.co.tripassistant.core.model.Recommendation
import uk.co.tripassistant.core.text.Rect01
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The floating recommendation window (spec sections 24 to 27).
 *
 * Design decisions worth knowing:
 *  * it defaults to the *top* of the screen, because Uber's Accept control is at the bottom and
 *    the overlay must never sit on top of it (spec section 24);
 *  * status is a coloured dot **and** a word, never colour alone (spec sections 25 and 46);
 *  * a drag moves it and is remembered; a tap expands it; the expanded card collapses itself after
 *    a few seconds so it never needs a second interaction while driving (spec sections 26 and 48);
 *  * [overlayBounds] reports where the window is, so the analysis pipeline can cut it out of a
 *    whole-screen capture and never read its own figures back (spec section 27).
 *
 * Every method must be called on the main thread.
 */
class OverlayController(
    private val context: Context,
    private val onPositionChanged: (x: Int, y: Int) -> Unit
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var binding: OverlayAssistantBinding? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private val collapseHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val collapseRunnable = Runnable { setExpanded(false) }

    val isAttached: Boolean get() = binding != null

    fun canDraw(): Boolean = OverlayPermission.isGranted(context)

    @SuppressLint("ClickableViewAccessibility")
    fun attach(savedX: Int?, savedY: Int?, compact: Boolean, preferRightSide: Boolean) {
        if (binding != null || !canDraw()) return
        val manager = windowManager ?: return

        val inflated = OverlayAssistantBinding.inflate(LayoutInflater.from(context))
        val metrics = displaySize()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Default position: high on the screen, on the driver's preferred side, clear of
            // Uber's Accept control at the bottom (spec section 24).
            x = savedX ?: if (preferRightSide) (metrics.first * 0.42f).roundToInt() else DEFAULT_MARGIN_PX
            y = savedY ?: (metrics.second * 0.10f).roundToInt()
        }

        if (compact) {
            inflated.statusLabel.textSize = COMPACT_TEXT_SP
            inflated.statusMetrics.textSize = COMPACT_TEXT_SP
        }

        inflated.collapsedPill.setOnTouchListener(DragListener(params, manager, inflated.root))

        runCatching { manager.addView(inflated.root, params) }
            .onSuccess {
                binding = inflated
                layoutParams = params
            }
    }

    fun detach() {
        collapseHandler.removeCallbacks(collapseRunnable)
        val view = binding?.root ?: return
        runCatching { windowManager?.removeView(view) }
        binding = null
        layoutParams = null
        expanded = false
    }

    fun render(state: OverlayState) {
        val views = binding ?: return

        val (label, title, colorRes) = presentation(state.recommendation)
        views.statusLabel.text = label
        views.statusMetrics.text = state.metricsLine
        views.expandedTitle.text = title
        views.expandedReason.text = state.reasonLine.ifBlank {
            context.getString(R.string.overlay_waiting_detail)
        }

        val color = ContextCompat.getColor(context, colorRes)
        val dot = views.statusDot.background?.mutate()
        if (dot is GradientDrawable) {
            dot.setColor(color)
            views.statusDot.background = dot
        } else {
            views.statusDot.setBackgroundColor(color)
        }

        views.valueFare.text = state.fare
        views.valuePerMile.text = state.perMile
        views.valuePerHour.text = state.perHour
        views.valuePickup.text = state.pickup
        views.valueTrip.text = state.trip
        views.valueRider.text = state.rider
        views.valueProfile.text = state.profileLine
    }

    /**
     * Where the overlay is, in 0..1 screen coordinates, with a margin so a shadow or a rounded
     * corner cannot leave a stray character outside the excluded region.
     */
    fun overlayBounds(): Rect01? {
        val view = binding?.root ?: return null
        if (view.width == 0 || view.height == 0) return null
        val (screenWidth, screenHeight) = displaySize()
        if (screenWidth == 0 || screenHeight == 0) return null

        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect01(
            left = location[0].toFloat() / screenWidth,
            top = location[1].toFloat() / screenHeight,
            right = (location[0] + view.width).toFloat() / screenWidth,
            bottom = (location[1] + view.height).toFloat() / screenHeight
        ).expandedBy(BOUNDS_MARGIN)
    }

    /** Keeps the overlay on screen after a rotation or a display-size change (spec section 27). */
    fun clampIntoScreen() {
        val params = layoutParams ?: return
        val view = binding?.root ?: return
        val manager = windowManager ?: return
        val (screenWidth, screenHeight) = displaySize()
        val width = view.width.takeIf { it > 0 } ?: DEFAULT_WIDTH_PX
        val height = view.height.takeIf { it > 0 } ?: DEFAULT_HEIGHT_PX

        val clampedX = params.x.coerceIn(0, (screenWidth - width).coerceAtLeast(0))
        val clampedY = params.y.coerceIn(0, (screenHeight - height).coerceAtLeast(0))
        if (clampedX == params.x && clampedY == params.y) return

        params.x = clampedX
        params.y = clampedY
        runCatching { manager.updateViewLayout(view, params) }
        onPositionChanged(clampedX, clampedY)
    }

    private fun setExpanded(value: Boolean) {
        val views = binding ?: return
        expanded = value
        views.expandedCard.visibility = if (value) View.VISIBLE else View.GONE
        collapseHandler.removeCallbacks(collapseRunnable)
        if (value) {
            // Collapses itself, so the driver never has to tap twice mid-shift (spec section 26).
            collapseHandler.postDelayed(collapseRunnable, AUTO_COLLAPSE_MILLIS)
        }
    }

    /** Label, expanded title and colour for a recommendation — never colour on its own. */
    private fun presentation(recommendation: Recommendation?): Triple<String, String, Int> =
        when (recommendation) {
            Recommendation.GOOD -> Triple(
                context.getString(R.string.overlay_good),
                context.getString(R.string.overlay_good_title),
                R.color.status_good
            )

            Recommendation.BORDERLINE -> Triple(
                context.getString(R.string.overlay_borderline),
                context.getString(R.string.overlay_borderline_title),
                R.color.status_borderline
            )

            Recommendation.POOR -> Triple(
                context.getString(R.string.overlay_poor),
                context.getString(R.string.overlay_poor_title),
                R.color.status_poor
            )

            Recommendation.UNKNOWN -> Triple(
                context.getString(R.string.overlay_unknown),
                context.getString(R.string.overlay_unknown_title),
                R.color.status_unknown
            )

            null -> Triple(
                context.getString(R.string.overlay_waiting),
                context.getString(R.string.overlay_waiting),
                R.color.status_unknown
            )
        }

    private fun displaySize(): Pair<Int, Int> {
        val manager = windowManager ?: return 0 to 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = manager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val display = manager.defaultDisplay
            val point = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getRealSize(point)
            point.x to point.y
        }
    }

    /**
     * Drag to move, tap to expand. The two are told apart by touch slop, so a slightly shaky tap
     * in a moving car still expands the card instead of nudging the window.
     */
    private inner class DragListener(
        private val params: WindowManager.LayoutParams,
        private val manager: WindowManager,
        private val root: View
    ) : View.OnTouchListener {

        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var startX = 0
        private var startY = 0
        private var startTouchX = 0f
        private var startTouchY = 0f
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean = when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = params.x
                startY = params.y
                startTouchX = event.rawX
                startTouchY = event.rawY
                dragging = false
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - startTouchX).roundToInt()
                val dy = (event.rawY - startTouchY).roundToInt()
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                if (dragging) {
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { manager.updateViewLayout(root, params) }
                }
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    clampIntoScreen()
                    onPositionChanged(params.x, params.y)
                } else {
                    setExpanded(!expanded)
                    view.performClick()
                }
                true
            }

            else -> false
        }
    }

    private companion object {
        const val AUTO_COLLAPSE_MILLIS = 6_000L
        const val DEFAULT_MARGIN_PX = 16
        const val DEFAULT_WIDTH_PX = 320
        const val DEFAULT_HEIGHT_PX = 96
        const val COMPACT_TEXT_SP = 14f
        const val BOUNDS_MARGIN = 0.015f
    }
}
