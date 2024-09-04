package com.waqas028.watch_faces_wear_os_sample

/**
 * Renders watch face via data in Room database. Also, updates watch face state based on setting
 * changes by user via [userStyleRepository.addUserStyleListener()].
 */
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyle
import androidx.wear.watchface.style.UserStyleSetting
import com.waqas028.watch_faces_wear_os_sample.utils.COLOR_STYLE_SETTING
import com.waqas028.watch_faces_wear_os_sample.utils.ColorStyleIdAndResourceIds
import com.waqas028.watch_faces_wear_os_sample.utils.DRAW_HOUR_PIPS_STYLE_SETTING
import com.waqas028.watch_faces_wear_os_sample.utils.WATCH_HAND_LENGTH_STYLE_SETTING
import com.waqas028.watch_faces_wear_os_sample.utils.WatchFaceColorPalette.Companion.convertToWatchFaceColorPalette
import com.waqas028.watch_faces_wear_os_sample.utils.WatchFaceData
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.max

private const val FRAME_PERIOD_MS_DEFAULT: Long = 16L

class AnalogWatchCanvasRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
) : Renderer.CanvasRenderer2<AnalogWatchCanvasRenderer.AnalogSharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    FRAME_PERIOD_MS_DEFAULT,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {
    class AnalogSharedAssets : SharedAssets {
        override fun onDestroy() {
        }
    }

    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Represents all data needed to render the watch face. All value defaults are constants. Only
    // three values are changeable by the user (color scheme, ticks being rendered, and length of
    // the minute arm). Those dynamic values are saved in the watch face APIs and we update those
    // here (in the renderer) through a Kotlin Flow.
    private var watchFaceData: WatchFaceData = WatchFaceData()

    // Converts resource ids into Colors and ComplicationDrawable.
    private var watchFaceColors = convertToWatchFaceColorPalette(
        context,
        watchFaceData.activeColorStyle,
        watchFaceData.ambientColorStyle
    )

    // Initializes paint object for painting the clock hands with default values.
    private val clockHandPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = context.resources.getDimensionPixelSize(R.dimen.clock_hand_stroke_width).toFloat()
    }

    private val outerElementPaint = Paint().apply {
        isAntiAlias = true
    }

    // Used to paint the main hour hand text with the hour pips, i.e., 3, 6, 9, and 12 o'clock.
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = context.resources.getDimensionPixelSize(R.dimen.hour_mark_size).toFloat()
    }

    private lateinit var hourHandFill: Path
    private lateinit var hourHandBorder: Path
    private lateinit var minuteHandFill: Path
    private lateinit var minuteHandBorder: Path
    private lateinit var secondHand: Path
    private var backgroundColor = 0

    // Changed when setting changes cause a change in the minute hand arm (triggered by user in
    // updateUserStyle() via userStyleRepository.addUserStyleListener()).
    private var armLengthChangedRecalculateClockHands: Boolean = false

    // Default size of watch face drawing area, that is, a no size rectangle. Will be replaced with
    // valid dimensions from the system.
    private var currentWatchFaceSize = Rect(0, 0, 0, 0)
    private var shouldUpdate = false
    private val timePaint = Paint().apply {
        color = context.getColor(R.color.white)
        textSize = 50f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val greetingPaint = Paint().apply {
        color = context.getColor(R.color.white)
        textSize = 12f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private var watchFaceAlignment = Alignment.Center
    private var dateTimeColor = Color.WHITE
    private var backgroundBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_image)

    init {
        scope.launch {
            currentUserStyleRepository.userStyle.collect { userStyle ->
                updateWatchFaceData(userStyle)
            }
        }
    }

    fun updateWatchFaces(bitmap: Bitmap){
        Log.i(TAG, "updateWatchFaces: ")
        backgroundBitmap = bitmap
        shouldUpdate = true
        invalidate() // Force redraw after 10 seconds
    }

    override suspend fun createSharedAssets(): AnalogSharedAssets {
        return AnalogSharedAssets()
    }

    /*
     * Triggered when the user makes changes to the watch face through the settings activity. The
     * function is called by a flow.
     */
    private fun updateWatchFaceData(userStyle: UserStyle) {
        Log.d(TAG, "updateWatchFace(): $userStyle")

        var newWatchFaceData: WatchFaceData = watchFaceData

        // Loops through user style and applies new values to watchFaceData.
        for (options in userStyle) {
            when (options.key.id.toString()) {
                COLOR_STYLE_SETTING -> {
                    val listOption = options.value as
                        UserStyleSetting.ListUserStyleSetting.ListOption

                    newWatchFaceData = newWatchFaceData.copy(
                        activeColorStyle = ColorStyleIdAndResourceIds.getColorStyleConfig(
                            listOption.id.toString()
                        )
                    )
                }
                DRAW_HOUR_PIPS_STYLE_SETTING -> {
                    val booleanValue = options.value as
                        UserStyleSetting.BooleanUserStyleSetting.BooleanOption

                    newWatchFaceData = newWatchFaceData.copy(
                        drawHourPips = booleanValue.value
                    )
                }
                WATCH_HAND_LENGTH_STYLE_SETTING -> {
                    val doubleValue = options.value as
                        UserStyleSetting.DoubleRangeUserStyleSetting.DoubleRangeOption

                    // The arm lengths are usually only calculated the first time the watch face is
                    // loaded to reduce the ops in the onDraw(). Because we updated the minute hand
                    // watch length, we need to trigger a recalculation.
                    armLengthChangedRecalculateClockHands = true

                    // Updates length of minute hand based on edits from user.
                    val newMinuteHandDimensions = newWatchFaceData.minuteHandDimensions.copy(
                        lengthFraction = doubleValue.value.toFloat()
                    )

                    newWatchFaceData = newWatchFaceData.copy(
                        minuteHandDimensions = newMinuteHandDimensions
                    )
                }
            }
        }

        // Only updates if something changed.
        if (watchFaceData != newWatchFaceData) {
            watchFaceData = newWatchFaceData

            // Recreates Color and ComplicationDrawable from resource ids.
            watchFaceColors = convertToWatchFaceColorPalette(
                context,
                watchFaceData.activeColorStyle,
                watchFaceData.ambientColorStyle
            )

            // Applies the user chosen complication color scheme changes. ComplicationDrawables for
            // each of the styles are defined in XML so we need to replace the complication's
            // drawables.
            for ((_, complication) in complicationSlotsManager.complicationSlots) {
                ComplicationDrawable.getDrawable(
                    context,
                    watchFaceColors.complicationStyleDrawableId
                )?.let {
                    (complication.renderer as CanvasComplicationDrawable).drawable = it
                }
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        scope.cancel("AnalogWatchCanvasRenderer scope clear() request")
        super.onDestroy()
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: AnalogSharedAssets
    ) {
        canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)

        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: AnalogSharedAssets
    ) {
        backgroundColor = if (renderParameters.drawMode == DrawMode.AMBIENT) {
            watchFaceColors.ambientBackgroundColor
        } else {
            watchFaceColors.activeBackgroundColor
        }
        canvas.drawColor(backgroundColor)

        if (shouldUpdate) {
            // Draw the time
            canvas.drawBitmapWithCrop(backgroundBitmap, bounds)
            //canvas.drawBitmap(backgroundBitmap, null, bounds, null)
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
            val dateFormatter = DateTimeFormatter.ofPattern("EEE d")
            val time = zonedDateTime.toLocalTime().format(timeFormatter)
            val date = zonedDateTime.toLocalDate().format(dateFormatter)

            val timePaint = Paint().apply {
                color = dateTimeColor
                textSize = 40f // Size of the text
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }
            canvas.drawDateTime(
                zonedDateTime = ZonedDateTime.now(),
                timePaint = timePaint,
                bounds = bounds,
                alignment = watchFaceAlignment, // Change to any alignment like Alignment.BottomLeft, Alignment.Center, etc.
                paddingDp = 10f, // Padding in dp
                density = context.resources.displayMetrics.density // Screen density
            )
        }
        else {
            // Draw the time
            canvas.drawBitmap(backgroundBitmap, null, bounds, null)
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
            val dateFormatter = DateTimeFormatter.ofPattern("EEE d")
            val time = zonedDateTime.toLocalTime().format(timeFormatter)
            val date = zonedDateTime.toLocalDate().format(dateFormatter)

            val timePaint = Paint().apply {
                color = dateTimeColor
                textSize = 40f // Size of the text
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }
            canvas.drawDateTime(
                zonedDateTime = ZonedDateTime.now(),
                timePaint = timePaint,
                bounds = bounds,
                alignment = watchFaceAlignment, // Change to any alignment like Alignment.BottomLeft, Alignment.Center, etc.
                paddingDp = 10f, // Padding in dp
                density = context.resources.displayMetrics.density // Screen density
            )

            /*// CanvasComplicationDrawable already obeys rendererParameters.
            drawComplications(canvas, zonedDateTime)

            if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.COMPLICATIONS_OVERLAY)) {
                drawClockHands(canvas, bounds, zonedDateTime)
            }

            if (renderParameters.drawMode == DrawMode.INTERACTIVE &&
                renderParameters.watchFaceLayers.contains(WatchFaceLayer.BASE) &&
                watchFaceData.drawHourPips
            ) {
                drawNumberStyleOuterElement(
                    canvas,
                    bounds,
                    watchFaceData.numberRadiusFraction,
                    watchFaceData.numberStyleOuterCircleRadiusFraction,
                    watchFaceColors.activeOuterElementColor,
                    watchFaceData.numberStyleOuterCircleRadiusFraction,
                    watchFaceData.gapBetweenOuterCircleAndBorderFraction
                )
            }*/
        }
    }

    private fun Canvas.drawBitmapWithCrop(
        bitmap: Bitmap,
        bounds: Rect
    ) {
        // Calculate the source rectangle (portion of the bitmap to be drawn)
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        val canvasAspect = bounds.width().toFloat() / bounds.height().toFloat()
        val bitmapAspect = bitmapWidth.toFloat() / bitmapHeight.toFloat()

        val srcRect: Rect
        if (bitmapAspect > canvasAspect) {
            // Bitmap is wider than the canvas, crop horizontally
            val cropWidth = (bitmapWidth - bitmapHeight * canvasAspect).toInt() / 2
            srcRect = Rect(cropWidth, 0, bitmapWidth - cropWidth, bitmapHeight)
        } else {
            // Bitmap is taller than the canvas, crop vertically
            val cropHeight = (bitmapHeight - bitmapWidth / canvasAspect).toInt() / 2
            srcRect = Rect(0, cropHeight, bitmapWidth, bitmapHeight - cropHeight)
        }

        // The destination rectangle is the bounds of the canvas
        val dstRect = bounds

        // Draw the bitmap using the calculated source and destination rectangles
        this.drawBitmap(bitmap, srcRect, dstRect, null)
    }

    private fun Canvas.drawDateTime(
        zonedDateTime: ZonedDateTime,
        timePaint: Paint,
        bounds: Rect,
        alignment: Alignment,
        paddingDp: Float = 10f,
        density: Float = 1f
    ) {
        // Format the time and date
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
        val dateFormatter = DateTimeFormatter.ofPattern("EEE d")
        val time = zonedDateTime.toLocalTime().format(timeFormatter)
        val date = zonedDateTime.toLocalDate().format(dateFormatter)

        // Calculate padding in pixels
        val padding = paddingDp * density

        // Get the bounds of the text
        val timeBounds = Rect()
        timePaint.getTextBounds(time, 0, time.length, timeBounds)
        val dateBounds = Rect()
        timePaint.getTextBounds(date, 0, date.length, dateBounds)

        // Calculate the maximum width and total height of the date and time
        val textWidth = max(timeBounds.width(), dateBounds.width())
        val textHeight = dateBounds.height() + timeBounds.height() + padding / 2

        // Calculate x for date and time based on alignment
        val xDate = when (alignment) {
            Alignment.TopLeft, Alignment.BottomLeft, Alignment.CenterStart -> padding
            Alignment.TopRight, Alignment.BottomRight, Alignment.CenterEnd -> bounds.width() - dateBounds.width() - padding
            Alignment.Center, Alignment.TopCenter, Alignment.BottomCenter -> (bounds.width() - dateBounds.width()) / 2f
            else -> padding // Fallback to TopLeft
        }

        val xTime = when (alignment) {
            Alignment.TopLeft, Alignment.BottomLeft, Alignment.CenterStart -> padding
            Alignment.TopRight, Alignment.BottomRight, Alignment.CenterEnd -> bounds.width() - timeBounds.width() - padding
            Alignment.Center, Alignment.TopCenter, Alignment.BottomCenter -> (bounds.width() - timeBounds.width()) / 2f
            else -> padding // Fallback to TopLeft
        }

        // Calculate y based on alignment and add padding from the top and bottom as needed
        val y = when (alignment) {
            Alignment.TopLeft, Alignment.TopCenter, Alignment.TopRight -> padding + dateBounds.height()
            Alignment.BottomLeft, Alignment.BottomCenter, Alignment.BottomRight -> bounds.height() - textHeight - padding
            Alignment.Center, Alignment.CenterStart, Alignment.CenterEnd -> (bounds.height() - textHeight) / 2f + padding + dateBounds.height()
            else -> padding + dateBounds.height() // Fallback to TopLeft
        }

        // Draw the date and time on the canvas with calculated positions
        this.drawText(date, xDate, y, timePaint)
        this.drawText(time, xTime, y + timeBounds.height() + padding / 2, timePaint)
    }

    // ----- All drawing functions -----
    private fun drawComplications(canvas: Canvas, zonedDateTime: ZonedDateTime) {
        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.render(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    private fun drawClockHands(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime
    ) {
        // Only recalculate bounds (watch face size/surface) has changed or the arm of one of the
        // clock hands has changed (via user input in the settings).
        // NOTE: Watch face surface usually only updates one time (when the size of the device is
        // initially broadcasted).
        if (currentWatchFaceSize != bounds || armLengthChangedRecalculateClockHands) {
            armLengthChangedRecalculateClockHands = false
            currentWatchFaceSize = bounds
            recalculateClockHands(bounds)
        }

        // Retrieve current time to calculate location/rotation of watch arms.
        val secondOfDay = zonedDateTime.toLocalTime().toSecondOfDay()

        // Determine the rotation of the hour and minute hand.

        // Determine how many seconds it takes to make a complete rotation for each hand
        // It takes the hour hand 12 hours to make a complete rotation
        val secondsPerHourHandRotation = Duration.ofHours(12).seconds
        // It takes the minute hand 1 hour to make a complete rotation
        val secondsPerMinuteHandRotation = Duration.ofHours(1).seconds

        // Determine the angle to draw each hand expressed as an angle in degrees from 0 to 360
        // Since each hand does more than one cycle a day, we are only interested in the remainder
        // of the secondOfDay modulo the hand interval
        val hourRotation = secondOfDay.rem(secondsPerHourHandRotation) * 360.0f /
            secondsPerHourHandRotation
        val minuteRotation = secondOfDay.rem(secondsPerMinuteHandRotation) * 360.0f /
            secondsPerMinuteHandRotation

        canvas.withScale(
            x = WATCH_HAND_SCALE,
            y = WATCH_HAND_SCALE,
            pivotX = bounds.exactCenterX(),
            pivotY = bounds.exactCenterY()
        ) {
            val drawAmbient = renderParameters.drawMode == DrawMode.AMBIENT

            clockHandPaint.style = if (drawAmbient) Paint.Style.STROKE else Paint.Style.FILL
            clockHandPaint.color = if (drawAmbient) {
                watchFaceColors.ambientPrimaryColor
            } else {
                watchFaceColors.activePrimaryColor
            }

            // Draw hour hand.
            withRotation(hourRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
                drawPath(hourHandBorder, clockHandPaint)
            }

            // Draw minute hand.
            withRotation(minuteRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
                drawPath(minuteHandBorder, clockHandPaint)
            }

            // Draw second hand if not in ambient mode
            if (!drawAmbient) {
                clockHandPaint.color = watchFaceColors.activeSecondaryColor

                // Second hand has a different color style (secondary color) and is only drawn in
                // active mode, so we calculate it here (not above with others).
                val secondsPerSecondHandRotation = Duration.ofMinutes(1).seconds
                val secondsRotation = secondOfDay.rem(secondsPerSecondHandRotation) * 360.0f /
                    secondsPerSecondHandRotation
                clockHandPaint.color = watchFaceColors.activeSecondaryColor

                withRotation(secondsRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
                    drawPath(secondHand, clockHandPaint)
                }
            }
        }
    }

    /*
     * Rarely called (only when watch face surface changes; usually only once) from the
     * drawClockHands() method.
     */
    private fun recalculateClockHands(bounds: Rect) {
        Log.d(TAG, "recalculateClockHands()")
        hourHandBorder =
            createClockHand(
                bounds,
                watchFaceData.hourHandDimensions.lengthFraction,
                watchFaceData.hourHandDimensions.widthFraction,
                watchFaceData.gapBetweenHandAndCenterFraction,
                watchFaceData.hourHandDimensions.xRadiusRoundedCorners,
                watchFaceData.hourHandDimensions.yRadiusRoundedCorners
            )
        hourHandFill = hourHandBorder

        minuteHandBorder =
            createClockHand(
                bounds,
                watchFaceData.minuteHandDimensions.lengthFraction,
                watchFaceData.minuteHandDimensions.widthFraction,
                watchFaceData.gapBetweenHandAndCenterFraction,
                watchFaceData.minuteHandDimensions.xRadiusRoundedCorners,
                watchFaceData.minuteHandDimensions.yRadiusRoundedCorners
            )
        minuteHandFill = minuteHandBorder

        secondHand =
            createClockHand(
                bounds,
                watchFaceData.secondHandDimensions.lengthFraction,
                watchFaceData.secondHandDimensions.widthFraction,
                watchFaceData.gapBetweenHandAndCenterFraction,
                watchFaceData.secondHandDimensions.xRadiusRoundedCorners,
                watchFaceData.secondHandDimensions.yRadiusRoundedCorners
            )
    }

    /**
     * Returns a round rect clock hand if {@code rx} and {@code ry} equals to 0, otherwise return a
     * rect clock hand.
     *
     * @param bounds The bounds use to determine the coordinate of the clock hand.
     * @param length Clock hand's length, in fraction of {@code bounds.width()}.
     * @param thickness Clock hand's thickness, in fraction of {@code bounds.width()}.
     * @param gapBetweenHandAndCenter Gap between inner side of arm and center.
     * @param roundedCornerXRadius The x-radius of the rounded corners on the round-rectangle.
     * @param roundedCornerYRadius The y-radius of the rounded corners on the round-rectangle.
     */
    private fun createClockHand(
        bounds: Rect,
        length: Float,
        thickness: Float,
        gapBetweenHandAndCenter: Float,
        roundedCornerXRadius: Float,
        roundedCornerYRadius: Float
    ): Path {
        val width = bounds.width()
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val left = centerX - thickness / 2 * width
        val top = centerY - (gapBetweenHandAndCenter + length) * width
        val right = centerX + thickness / 2 * width
        val bottom = centerY - gapBetweenHandAndCenter * width
        val path = Path()

        if (roundedCornerXRadius != 0.0f || roundedCornerYRadius != 0.0f) {
            path.addRoundRect(
                left,
                top,
                right,
                bottom,
                roundedCornerXRadius,
                roundedCornerYRadius,
                Path.Direction.CW
            )
        } else {
            path.addRect(
                left,
                top,
                right,
                bottom,
                Path.Direction.CW
            )
        }
        return path
    }

    private fun drawNumberStyleOuterElement(
        canvas: Canvas,
        bounds: Rect,
        numberRadiusFraction: Float,
        outerCircleStokeWidthFraction: Float,
        outerElementColor: Int,
        numberStyleOuterCircleRadiusFraction: Float,
        gapBetweenOuterCircleAndBorderFraction: Float
    ) {
        // Draws text hour indicators (12, 3, 6, and 9).
        val textBounds = Rect()
        textPaint.color = outerElementColor
        for (i in 0 until 4) {
            val rotation = 0.5f * (i + 1).toFloat() * Math.PI
            val dx = sin(rotation).toFloat() * numberRadiusFraction * bounds.width().toFloat()
            val dy = -cos(rotation).toFloat() * numberRadiusFraction * bounds.width().toFloat()
            textPaint.getTextBounds(HOUR_MARKS[i], 0, HOUR_MARKS[i].length, textBounds)
            canvas.drawText(
                HOUR_MARKS[i],
                bounds.exactCenterX() + dx - textBounds.width() / 2.0f,
                bounds.exactCenterY() + dy + textBounds.height() / 2.0f,
                textPaint
            )
        }

        // Draws dots for the remain hour indicators between the numbers above.
        outerElementPaint.strokeWidth = outerCircleStokeWidthFraction * bounds.width()
        outerElementPaint.color = outerElementColor
        canvas.save()
        for (i in 0 until 12) {
            if (i % 3 != 0) {
                drawTopMiddleCircle(
                    canvas,
                    bounds,
                    numberStyleOuterCircleRadiusFraction,
                    gapBetweenOuterCircleAndBorderFraction
                )
            }
            canvas.rotate(360.0f / 12.0f, bounds.exactCenterX(), bounds.exactCenterY())
        }
        canvas.restore()
    }

    /** Draws the outer circle on the top middle of the given bounds. */
    private fun drawTopMiddleCircle(
        canvas: Canvas,
        bounds: Rect,
        radiusFraction: Float,
        gapBetweenOuterCircleAndBorderFraction: Float
    ) {
        outerElementPaint.style = Paint.Style.FILL_AND_STROKE

        // X and Y coordinates of the center of the circle.
        val centerX = 0.5f * bounds.width().toFloat()
        val centerY = bounds.width() * (gapBetweenOuterCircleAndBorderFraction + radiusFraction)

        canvas.drawCircle(
            centerX,
            centerY,
            radiusFraction * bounds.width(),
            outerElementPaint
        )
    }

    companion object {
        private const val TAG = "AnalogWatchCanvasRenderer"

        // Painted between pips on watch face for hour marks.
        private val HOUR_MARKS = arrayOf("3", "6", "9", "12")

        // Used to canvas.scale() to scale watch hands in proper bounds. This will always be 1.0.
        private const val WATCH_HAND_SCALE = 1.0f
    }
}



class MyOtherRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
) : Renderer.CanvasRenderer2<AnalogWatchCanvasRenderer.AnalogSharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    FRAME_PERIOD_MS_DEFAULT,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false
){
    override suspend fun createSharedAssets(): AnalogWatchCanvasRenderer.AnalogSharedAssets {
        TODO("Not yet implemented")
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: AnalogWatchCanvasRenderer.AnalogSharedAssets
    ) {
        TODO("Not yet implemented")
    }

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: AnalogWatchCanvasRenderer.AnalogSharedAssets
    ) {
        drawComplications(canvas, zonedDateTime)
    }

    private fun drawComplications(canvas: Canvas, zonedDateTime: ZonedDateTime) {
        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.render(canvas, zonedDateTime, renderParameters)
            }
        }
    }
}
enum class Alignment {
    TopLeft, TopCenter, TopRight,
    CenterLeft, Center, CenterRight,
    BottomLeft, BottomCenter, BottomRight, CenterStart, CenterEnd, CenterHorizontally
}