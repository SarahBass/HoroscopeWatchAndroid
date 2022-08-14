package com.academy.horoscopewatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.palette.graphics.Palette
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.time.temporal.TemporalField
import java.util.*
import kotlin.math.abs

/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

private const val HOUR_STROKE_WIDTH = 7f
private const val MINUTE_STROKE_WIDTH = 3f
private const val SECOND_TICK_STROKE_WIDTH = 2f

private const val CENTER_GAP_AND_CIRCLE_RADIUS = 4f

private const val SHADOW_RADIUS = 6f

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn"t
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class MyWatchFace : CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: Engine) : Handler() {
        private val mWeakReference: WeakReference<Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {

        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false
        private var mCenterX: Float = 0F
        private var mCenterY: Float = 0F

        private var mSecondHandLength: Float = 0F
        private var sMinuteHandLength: Float = 0F
        private var sHourHandLength: Float = 0F

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private var mWatchHandColor: Int = 0
        private var mWatchHandHighlightColor: Int = 0
        private var mWatchHandShadowColor: Int = 0

        private lateinit var mHourPaint: Paint
        private lateinit var mMinutePaint: Paint
        private lateinit var mSecondPaint: Paint
        private lateinit var mTickAndCirclePaint: Paint

        private lateinit var mBackgroundPaint: Paint
        private lateinit var mBackgroundBitmap: Bitmap
        private lateinit var mGrayBackgroundBitmap: Bitmap

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()

            initializeBackground()
            initializeWatchFace()
        }

        private fun getMoonPhase(): String {
            val d = Date()
            val sdf1 = SimpleDateFormat("d")
            val dayOfMonth: String = sdf1.format(d)
            val LUNAR_MONTH = 29.530588853;
            val newMoondifference = abs((Integer.parseInt(dayOfMonth)) - (Integer.parseInt(getnewMoonDate())))
            val moonPercent : Double = newMoondifference / LUNAR_MONTH
            val moonString : String = if(moonPercent < 0.05 ){"New Moon"}
            else if (moonPercent >= .05 && moonPercent < 0.25 ){"Waxing Crescent Moon"}
            else if(moonPercent >=0.25 && moonPercent < 0.35){"Waxing Half Moon"}
            else if(moonPercent >=0.35 && moonPercent < 0.47){"Waxing Gibbous Moon"}
            else if(moonPercent >=0.47 && moonPercent < 0.55){"Full Moon"}
            else if(moonPercent >=0.55 && moonPercent < 0.65){"Waning Gibbous Moon"}
            else if(moonPercent >=0.65 && moonPercent < 0.75){"Waning half Moon"}
            else if(moonPercent >=0.75 && moonPercent < 0.95){"Waning Crescent Moon"}
            else {"New Moon"}
            return moonString
        }




        private fun initializeBackground() {
            mBackgroundPaint = Paint().apply {
                color = Color.BLACK
            }
            mBackgroundBitmap = getAstrologyBackground()

            /* Extracts colors from background image to improve watchface style. */
            Palette.from(mBackgroundBitmap).generate {
                it?.let {
                    mWatchHandHighlightColor = it.getVibrantColor(Color.RED)
                    mWatchHandColor = it.getLightVibrantColor(Color.WHITE)
                    mWatchHandShadowColor = it.getDarkMutedColor(Color.BLACK)
                    updateWatchHandStyle()
                }


            }
        }

        private fun initializeWatchFace() {
            /* Set defaults for colors */
            mWatchHandColor = Color.WHITE
            mWatchHandHighlightColor = Color.WHITE
            mWatchHandShadowColor = Color.BLACK

            mHourPaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = HOUR_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }

            mMinutePaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = MINUTE_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }

            mSecondPaint = Paint().apply {
                color = mWatchHandHighlightColor
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }

            mTickAndCirclePaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                style = Paint.Style.STROKE
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                PROPERTY_LOW_BIT_AMBIENT, false
            )
            mBurnInProtection = properties.getBoolean(
                PROPERTY_BURN_IN_PROTECTION, false
            )
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateWatchHandStyle()

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        private fun updateWatchHandStyle() {
            if (mAmbient) {
                mHourPaint.color = Color.WHITE
                mMinutePaint.color = Color.WHITE
                mSecondPaint.color = Color.WHITE
                mTickAndCirclePaint.color = Color.WHITE

                mHourPaint.isAntiAlias = false
                mMinutePaint.isAntiAlias = false
                mSecondPaint.isAntiAlias = false
                mTickAndCirclePaint.isAntiAlias = false

                mHourPaint.clearShadowLayer()
                mMinutePaint.clearShadowLayer()
                mSecondPaint.clearShadowLayer()
                mTickAndCirclePaint.clearShadowLayer()

            } else {
                mHourPaint.color = mWatchHandColor
                mMinutePaint.color = mWatchHandColor
                mSecondPaint.color = mWatchHandHighlightColor
                mTickAndCirclePaint.color = mWatchHandColor

                mHourPaint.isAntiAlias = true
                mMinutePaint.isAntiAlias = true
                mSecondPaint.isAntiAlias = true
                mTickAndCirclePaint.isAntiAlias = true

                mHourPaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
                mMinutePaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
                mSecondPaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
                mTickAndCirclePaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                mHourPaint.alpha = if (inMuteMode) 100 else 255
                mMinutePaint.alpha = if (inMuteMode) 100 else 255
                mSecondPaint.alpha = if (inMuteMode) 80 else 255
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f
            mCenterY = height / 2f

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (mCenterX * 0.7).toFloat()
            sMinuteHandLength = (mCenterX * 0.6).toFloat()
            sHourHandLength = (mCenterX * 0.6).toFloat()

            /* Scale loaded background image (more efficient) if surface dimensions change. */
            val scale = width.toFloat() / mBackgroundBitmap.width.toFloat()

            mBackgroundBitmap = Bitmap.createScaledBitmap(
                mBackgroundBitmap,
                (mBackgroundBitmap.width * scale).toInt(),
                (mBackgroundBitmap.height * scale).toInt(), true
            )

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don"t want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren"t
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap()
            }
        }

        private fun initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                mBackgroundBitmap.width,
                mBackgroundBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(mGrayBackgroundBitmap)
            val grayPaint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(colorMatrix)
            grayPaint.colorFilter = filter
            canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, grayPaint)
        }



        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            drawBackground(canvas)
            drawWatchFace(canvas)
        }

        private fun drawBackground(canvas: Canvas) {

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK)
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            }
        }

        private fun drawWatchFace(canvas: Canvas) {

            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            val innerTickRadius = mCenterX - 10
            val outerTickRadius = mCenterX
           /* for (tickIndex in 0..11) {
                val tickRot = (tickIndex.toDouble() * Math.PI * 2.0 / 12).toFloat()
                val innerX = Math.sin(tickRot.toDouble()).toFloat() * innerTickRadius
                val innerY = (-Math.cos(tickRot.toDouble())).toFloat() * innerTickRadius
                val outerX = Math.sin(tickRot.toDouble()).toFloat() * outerTickRadius
                val outerY = (-Math.cos(tickRot.toDouble())).toFloat() * outerTickRadius
                canvas.drawLine(
                    mCenterX + innerX, mCenterY + innerY,
                    mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint
                )
            } */

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            val seconds =
                mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f
            val secondsRotation = seconds * 6f

            val minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f

            val hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f
            val hoursRotation = mCalendar.get(Calendar.HOUR) * 30 + hourHandOffset

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save()

            canvas.rotate(hoursRotation, mCenterX, mCenterY)
            canvas.drawLine(
                mCenterX,
                mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                mCenterX,
                mCenterY - sHourHandLength,
                mHourPaint
            )

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY)
            canvas.drawLine(
                mCenterX,
                mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                mCenterX,
                mCenterY - sMinuteHandLength,
                mMinutePaint
            )

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY)
                canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - mSecondHandLength,
                    mSecondPaint
                )

            }
            canvas.drawCircle(
                mCenterX,
                mCenterY,
                CENTER_GAP_AND_CIRCLE_RADIUS,
                mTickAndCirclePaint
            )

            /* Restore the canvas" original orientation. */
            canvas.restore()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren"t visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            val frameTime = INTERACTIVE_UPDATE_RATE_MS
            val sdf = SimpleDateFormat("EEE")
            val sdf1 = SimpleDateFormat("EEEE")
            val sdf2 = SimpleDateFormat("MMMM")
            val sdf3 = SimpleDateFormat("d")
            val sdf4 = SimpleDateFormat("yyyy")
            val sdf5 = SimpleDateFormat("MMMM d yyyy")
            val sdf6 = SimpleDateFormat("h:m:s a")
            val sdf7 = SimpleDateFormat("a")
            val d = Date()
            val dayOfTheWeek: String = sdf.format(d)
            val dayOfTheWeekLong: String = sdf1.format(d)
            val monthOfYear: String = sdf2.format(d)
            val dayOfMonth: String = sdf3.format(d)
            val year4digits: String = sdf4.format(d)
            val fullDateSpaces: String = sdf5.format(d)
            val timeSpecific : String = sdf6.format(d)
            val amPM : String = sdf7.format(d)

            //Shows different methods to call strings
            when (tapType) {
                TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                TAP_TYPE_TAP ->
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    when ((mCalendar.timeInMillis % (8 * frameTime)) / frameTime) {
                        0L -> when (getHoroscope()){
                            "Aquarius" -> Toast.makeText(applicationContext, R.string.horoscope0, Toast.LENGTH_SHORT)
                            "Aries" -> Toast.makeText(applicationContext, R.string.horoscope1, Toast.LENGTH_SHORT)
                            "Cancer" -> Toast.makeText(applicationContext, R.string.horoscope2, Toast.LENGTH_SHORT)
                            "Capricorn" -> Toast.makeText(applicationContext, R.string.horoscope3, Toast.LENGTH_SHORT)
                            "Gemini" -> Toast.makeText(applicationContext, R.string.horoscope4, Toast.LENGTH_SHORT)
                            "Leo" -> Toast.makeText(applicationContext, R.string.horoscope5, Toast.LENGTH_SHORT)
                            "Libra" -> Toast.makeText(applicationContext, R.string.horoscope6, Toast.LENGTH_SHORT)
                            "Pisces" -> Toast.makeText(applicationContext, R.string.horoscope7, Toast.LENGTH_SHORT)
                            "Sagittarius" -> Toast.makeText(applicationContext, R.string.horoscope8, Toast.LENGTH_SHORT)
                            "Scorpio" -> Toast.makeText(applicationContext, R.string.horoscope9, Toast.LENGTH_SHORT)
                            "Taurus" -> Toast.makeText(applicationContext, R.string.horoscope10, Toast.LENGTH_SHORT)
                            "Virgo" -> Toast.makeText(applicationContext, R.string.horoscope11, Toast.LENGTH_SHORT)
                            else -> Toast.makeText(applicationContext, R.string.horoscope2, Toast.LENGTH_SHORT)}
                        1L -> Toast.makeText(applicationContext,
                            "$dayOfTheWeek , $fullDateSpaces", Toast.LENGTH_SHORT)
                        2L -> Toast.makeText(applicationContext, timeSpecific, Toast.LENGTH_SHORT)
                        3L -> Toast.makeText(applicationContext, getMoonPhase(), Toast.LENGTH_SHORT)
                        4L -> if(getPlanetEventTYPE() == "none"){
                            Toast.makeText(applicationContext, getPlanetEvent3(), Toast.LENGTH_SHORT)
                        }else{
                            Toast.makeText(applicationContext, getPlanetEvent(), Toast.LENGTH_SHORT)
                        }
                        5L -> if(getPlanetEventTYPE2() == "none"){
                            Toast.makeText(applicationContext, getPlanetEvent3(), Toast.LENGTH_SHORT)
                        }else{
                            Toast.makeText(applicationContext, getPlanetEvent2(), Toast.LENGTH_SHORT)
                        }
                        6L -> Toast.makeText(applicationContext, getPlanetEvent3(), Toast.LENGTH_SHORT)
                        7L -> Toast.makeText(applicationContext, getPlanetEvent1() + ": "+ monthOfYear + " " + getFullMoonDate() + "th", Toast.LENGTH_SHORT)
                        else ->  Toast.makeText(applicationContext, " ", Toast.LENGTH_SHORT)}

                        .show()
            }
            invalidate()
        }

        private fun getFullMoonDate(): String {
            val d = Date()
            val sdf0 = SimpleDateFormat("yyyy MMMM")
            val yearMonth: String = sdf0.format(d)
            val fullMoonDate = when(yearMonth){
                "2022 January" -> "17"
                "2022 February" -> "16"
                "2022 March" -> "18"
                "2022 April" -> "16"
                "2022 May" -> "16"
                "2022 June" -> "14"
                "2022 July" -> "13"
                "2022 August" -> "11"
                "2022 September" -> "10"
                "2022 October" -> "9"
                "2022 November" -> "8"
                "2022 December" -> "7"
                "2023 January" -> "6"
                "2023 February" -> "5"
                "2023 March" -> "7"
                "2023 April" -> "5"
                "2023 May" -> "5"
                "2023 June" -> "3"
                "2023 July" -> "3"
                "2023 August" -> "1"
                "2023 September" -> "29"
                "2023 October" -> "28"
                "2023 November" -> "27"
                "2023 December" -> "26"
                "2024 January" -> "25"
                "2024 February" -> "24"
                "2024 March" -> "25"
                "2024 April" -> "23"
                "2024 May" -> "23"
                "2024 June" -> "21"
                "2024 July" -> "21"
                "2024 August" -> "19"
                "2024 September" -> "17"
                "2024 October" -> "17"
                "2024 November" -> "15"
                "2024 December" -> "15"
                "2025 January" -> "13"
                "2025 February" -> "12"
                "2025 March" -> "13"
                "2025 April" -> "12"
                "2025 May" -> "12"
                "2025 June" -> "11"
                "2025 July" -> "10"
                "2025 August" -> "9"
                "2025 September" -> "7"
                "2025 October" -> "6"
                "2025 November" -> "5"
                "2025 December" -> "4"
                "2026 January" -> "3"
                "2026 February" -> "1"
                "2026 March" -> "3"
                "2026 April" -> "1"
                "2026 May" -> "1"
                "2026 June" -> "29"
                "2026 July" -> "29"
                "2026 August" -> "27"
                "2026 September" -> "26"
                "2026 October" -> "25"
                "2026 November" -> "24"
                "2026 December" -> "23"
                "2027 January" -> "22"
                "2027 February" -> "20"
                "2027 March" -> "22"
                "2027 April" -> "20"
                "2027 May" -> "20"
                "2027 June" -> "18"
                "2027 July" -> "18"
                "2027 August" -> "17"
                "2027 September" -> "15"
                "2027 October" -> "15"
                "2027 November" -> "13"
                "2027 December" -> "13"
                "2028 January" -> "11"
                "2028 February" -> "10"
                "2028 March" -> "10"
                "2028 April" -> "9"
                "2028 May" -> "8"
                "2028 June" -> "6"
                "2028 July" -> "6"
                "2028 August" -> "5"
                "2028 September" -> "3"
                "2028 October" -> "3"
                "2028 November" -> "2"
                "2028 December" -> "1"
                else -> "1"
            }
            return fullMoonDate
        }

        private fun getnewMoonDate(): String {
            val d = Date()
            val sdf0 = SimpleDateFormat("yyyy MMMM")
            val yearMonth: String = sdf0.format(d)
            val newMoonDate = when(yearMonth){
                "2022 January" -> 2
                "2022 February" -> 1
                "2022 March" -> 2
                "2022 April" -> 1
                "2022 May" -> 30
                "2022 June" -> 28
                "2022 July" -> 28
                "2022 August" -> 27
                "2022 September" -> 25
                "2022 October" -> 25
                "2022 November" -> 23
                "2022 December" -> 23
                "2023 January" -> 21
                "2023 February" -> 19
                "2023 March" -> 21
                "2023 April" -> 19
                "2023 May" -> 19
                "2023 June" -> 17
                "2023 July" -> 17
                "2023 August" -> 16
                "2023 September" -> 14
                "2023 October" -> 14
                "2023 November" -> 13
                "2023 December" -> 12
                else -> 1
            }
            return newMoonDate.toString()
        }


        private fun getPlanetEvent(): String {
            val d = Date()
            val sdf0 = SimpleDateFormat("yyyy MMMM")
            val yearMonth: String = sdf0.format(d)
            val planetOpposition =
                when(yearMonth){
                    "2022 January" -> "Jupiter in Pisces January 1 to May 9, 2022" //Mercury Visible at Sunset
                    "2022 February" -> "Venus Brightest on February 9, 2022" //Pluto returns. This happens only once 248 years
                    "2022 March" -> "March 20 - March Equinox" //March 18: Worm Moon
                    "2022 April" -> "Mercury will be visible at Sunrise" //April 16: Pink Moon
                    "2022 May" -> "May 16: Flower Moon" // May 5/6: Eta Aquarid Meteors
                    "2022 June" -> "June 28, 2022: Neptune begins retrograde motion" //Mercury Visible at Sunrise
                    "2022 July" -> "July 28, 2022: Jupiter begins retrograde motion" // Pluto at Opposition 20 Jul 2022
                    "2022 August" -> "Saturn is in Opposition on August 14" //August 24, 2022: Uranus begins retrograde motion
                    "2022 September" -> "Jupiter opposition 2022 – September 26" //Septemper 16, 2022: Neptune at opposition
                    "2022 October" -> "Saturn ends retrograde motion" //Mars in Retrograde October 30, 2022
                    "2022 November" -> "November 23, 2022: Jupiter ends retrograde motion" // 2022 Uranus opposition – November 9
                    "2022 December" -> "Mars is in Opposition on December 8" //Dec 21: December Solstice

                    "2023 January" -> "Uranus ends retrograde motion" //Jan 7, 2023: Inferior conjunction Mercury
                    "2023 February" -> "February 16, 2023: Saturn in conjunction with the sun"
                    "2023 March" -> "Neptune at solar conjunction" //Uranus at solar conjunction
                    "2023 April" -> "April 11, 2023: Jupiter at solar conjunction"
                    "2023 May" -> "Venus Brightest on 12 May 2023" // Mercury Visible at Sunrise
                    "2023 June" -> "Neptune begins retrograde motion"
                    "2023 July" -> "Pluto at Opposition : 22 Jul 2023"
                    "2023 August" -> "Saturn is in Opposition on August 27" // Uranus begins retrograde motion
                    "2023 September" -> "September 19, 2023: Neptune at opposition"
                    "2023 October" -> "October 21, 22 - Orionids Meteor Shower"
                    "2023 November" -> "Jupiter opposition 2023 – November 2" // 2023 Uranus opposition – November 13
                    "2023 December" -> "December 13, 14 - Geminids Meteor Shower"

                    "2024 January" -> "Full Wolf Moon Jan 25th"
                    "2024 February" -> "Full Snow Moon Feb 24th"
                    "2024 March" -> "Mercury Visible at Sunset"
                    "2024 April" -> "Full Pink Moon April 23"
                    "2024 May" -> "Mercury Visible at Sunrise"
                    "2024 June" -> "Venus at superior solar conjunction : 04 Jun 2024"
                    "2024 July" -> "Pluto at Opposition : 23 Jul 2024" //Mercury visible at Sunset
                    "2024 August" -> "Full Sturgeon Moon Aug 19 "
                    "2024 September" -> "Saturn is in Opposition on September 8" //Mercury visible at Sunrise
                    "2024 October" -> "Full Harvest Moon October 17"
                    "2024 November" -> "2024 Uranus opposition – November 16" //Mercury visible at sunset
                    "2024 December" -> "Jupiter opposition 2024 – December 7"

                    "2025 January" -> "Jupiter opposition 2025 – January 10" // "Mars is in Opposition on January 16th"
                    "2025 February" -> "Venus at greatest brightness: 16 Feb 2025"
                    "2025 March" -> "Mercury visible at Sunset"
                    "2025 April" -> "April 12, 2025: Full Pink Moon"
                    "2025 May" -> "May 12, 2025: Full Flower Moon"
                    "2025 June" -> "June 11, 2025: Full Strawberry Moon"
                    "2025 July" -> "Pluto at Opposition :25 Jul 2025"
                    "2025 August" -> "August 9, 2025: Full Corn Moon"
                    "2025 September" -> "Saturn is in Opposition on September 25"
                    "2025 October" -> "October 6, 2025: Full Harvest Moon"
                    "2025 November" -> "2025 Uranus opposition – November 21"
                    "2025 December" -> "December 13, 14 - Geminids Meteor Shower"

                    else -> "none"
                }
            return planetOpposition

        }
        private fun getPlanetEvent1(): String {
            val d = Date()
            val sdf0 = SimpleDateFormat("MMMM")
            val Month: String = sdf0.format(d)
            val planetOpposition =
                when(Month){
                    "January" -> "Wolf Moon"
                    "February" -> "Snow Moon"
                    "March" -> "Worm Moon"
                    "April" -> "Pink Moon"
                    "May" -> "Flower Moon"
                    "June" -> "Strawberry Moon"
                    "July" -> "Buck Moon"
                    "August" -> "Sturgeon Moon"
                    "September" -> "Corn Moon"
                    "October" -> "Harvest Moon"
                    "November" -> "Beaver Moon"
                    "December" -> "Cold Moon"
                    else -> "None"
                }
            return planetOpposition}

        private fun getPlanetEvent2(): String {
            val d = Date()
            val sdf0 = SimpleDateFormat("yyyy MMMM")
            val yearMonth: String = sdf0.format(d)
            val planetOpposition =
                when(yearMonth){
                    "2022 January" -> "Mercury Visible at Sunset"
                    "2022 February" -> "Pluto returns. This happens only once 248 years"
                    "2022 March" -> "March 18: Worm Moon"
                    "2022 April" -> "April 16: Pink Moon"
                    "2022 May" -> "May 5/6: Eta Aquarid Meteors"
                    "2022 June" -> "Mercury Visible at Sunrise"
                    "2022 July" -> "Pluto at Opposition 20 Jul 2022"
                    "2022 August" -> "August 24, 2022: Uranus begins retrograde motion"
                    "2022 September" -> "Septemper 16, 2022: Neptune at opposition"
                    "2022 October" -> "Mars in Retrograde October 30, 2022"
                    "2022 November" -> "2022 Uranus opposition – November 9"
                    "2022 December" -> "Dec 21: December Solstice"

                    "2023 January" -> "Jan 7, 2023: Inferior conjunction Mercury"
                    "2023 February" -> "February 5 , 10:30 am Full Snow Moon "
                    "2023 March" -> "Uranus at solar conjunction"
                    "2023 April" -> "Full Pink Moon April 5th"
                    "2023 May" -> "Mercury Visible at Sunrise"
                    "2023 June" -> "June 21 2023 Summer Solstice"
                    "2023 July" -> "Full Buck Moon July 3rd"
                    "2023 August" -> "Uranus begins retrograde motion"
                    "2023 September" -> "Fall Equinox Sep 23 2023"
                    "2023 October" -> "Full Harvest Moon October 28 , 1:24 pm"
                    "2023 November" -> "2023 Uranus opposition – November 13"
                    "2023 December" -> "Winter Solstice Dec 21 2023"

                    "2024 January" -> "January 3, 4 - Quadrantids Meteor Shower"
                    "2024 February" -> "Snow Moon February 24 "
                    "2024 March" -> "Spring Equinox Mar 19 2024"
                    "2024 April" -> "April 8: Total Solar Eclipse parts of USA"
                    "2024 May" -> "May 6, 7 - Eta Aquarids Meteor Shower"
                    "2024 June" -> "Summer Solstice Jun 20 2024"
                    "2024 July" -> "Mercury visible at Sunset"
                    "2024 August" -> "August 12, 13 - Perseids Meteor Shower"
                    "2024 September" -> "Mercury visible at Sunrise"
                    "2024 October" -> "October 21, 22 - Orionids Meteor Shower"
                    "2024 November" -> "Mercury visible at sunset"
                    "2024 December" -> "Winter Solstice Dec 21 2024"

                    "2025 January" -> "Mars is in Opposition on January 16th"
                    "2025 February" -> "February 12 - Full Snow Moon"
                    "2025 March" -> "Spring Equinox Mar 20 2025"
                    "2025 April" -> "April 22, 23 - Lyrids Meteor Shower"
                    "2025 May" -> "May 6, 7 - Eta Aquarids Meteor Shower."
                    "2025 June" -> "Summer Solstice Jun 20 2025"
                    "2025 July" -> "July 28, 29 - Delta Aquarids Meteor Shower"
                    "2025 August" -> "August 12, 13 - Perseids Meteor Shower"
                    "2025 September" -> "Fall Equinox September 22 2025"
                    "2025 October" -> "October 21, 22 - Orionids Meteor Shower"
                    "2025 November" -> "November 4, 5 - Taurids Meteor Shower"
                    "2025 December" -> "Winter Solstice Dec 21 2025"

                    else -> "none"
                }
            return planetOpposition

        }
        private fun getPlanetEvent3(): String {
            val planetOpposition =
                when(getHoroscope()){
                    "Aries" -> "Monthly Ruling Planet: Mars"
                    "Taurus" -> "Monthly Ruling Planet: Venus"
                    "Gemini" -> "Monthly Ruling Planet: Mercury"
                    "Cancer" -> "Ruling in Sky: Moon"
                    "Leo" -> "Ruling in Sky: Sun"
                    "Virgo" -> "Monthly Ruling Planet: Mercury"
                    "Libra" -> "Monthly Ruling Planet: Venus"
                    "Scorpio" ->"Monthly Ruling Planet: Pluto"
                    "Sagittarius" -> "Monthly Ruling Planet: Jupiter"
                    "Capricorn" -> "Monthly Ruling Planet: Saturn"
                    "Aquarius" -> "Monthly Ruling Planet: Uranus"
                    "Pisces" -> "Monthly Ruling Planet: Neptune"
                    else -> "Monthly Ruling Planet: Saturn"
                }
            return planetOpposition

        }
        private fun getPlanetEventTYPE(): String {

            val planetType = when{
                getPlanetEvent().contains("Pink") -> "moonpink"
                getPlanetEvent().contains("Harvest") -> "moonharvest"
                getPlanetEvent().contains("Worm") -> "moonworm"
                getPlanetEvent().contains("Snow") -> "moonsnow"
                getPlanetEvent().contains("Cold") -> "mooncold"
                getPlanetEvent().contains("Corn") -> "mooncorn"
                getPlanetEvent().contains("Strawberry") -> "moonstrawberry"
                getPlanetEvent().contains("Wolf") -> "moonwolf"
                getPlanetEvent().contains("Sturgeon") -> "moonbanimal"
                getPlanetEvent().contains("Buck") -> "moonbanimal"
                getPlanetEvent().contains("Flower") -> "moonpink"
                getPlanetEvent().contains("Beaver") -> "moonbeaver"
                getPlanetEvent().contains("Solstice" )-> "sun"
                getPlanetEvent().contains("Equinox")-> "sun"
                getPlanetEvent().contains("solstice")-> "sun"
                getPlanetEvent().contains("equinox")-> "sun"
                getPlanetEvent().contains("Mercury")-> "mercury"
                getPlanetEvent().contains("Venus")-> "venus"
                getPlanetEvent().contains("Mars")-> "mars"
                getPlanetEvent().contains("Jupiter")-> "jupiter"
                getPlanetEvent().contains("Saturn")-> "saturn"
                getPlanetEvent().contains("Uranus")-> "uranus"
                getPlanetEvent().contains("Neptune")-> "neptune"
                getPlanetEvent().contains("Pluto")-> "pluto"
                getPlanetEvent().contains("Meteor")-> "shower"
                getPlanetEvent().contains("meteor")-> "shower"
                getPlanetEvent().contains("None")-> "none"
                else -> "none"
            }

            return planetType
        }

        private fun getPlanetEventTYPE1(): String {

            val planetType = when{
                getPlanetEvent().contains("Pink") -> "moonpink"
                getPlanetEvent().contains("Harvest") -> "moonharvest"
                getPlanetEvent().contains("Worm") -> "moonworm"
                getPlanetEvent().contains("Snow") -> "moonsnow"
                getPlanetEvent().contains("Cold") -> "mooncold"
                getPlanetEvent().contains("Corn") -> "mooncorn"
                getPlanetEvent().contains("Strawberry") -> "moonstrawberry"
                getPlanetEvent().contains("Wolf") -> "moonwolf"
                getPlanetEvent().contains("Sturgeon") -> "moonbanimal"
                getPlanetEvent().contains("Buck") -> "moonbanimal"
                getPlanetEvent().contains("Flower") -> "moonpink"
                getPlanetEvent().contains("Beaver") -> "moonbeaver"
                else -> "none"
            }

            return planetType
        }
        private fun getPlanetEventTYPE2(): String {

            val planetType2 : String = when{
                getPlanetEvent().contains("Pink") -> "moonpink"
                getPlanetEvent().contains("Harvest") -> "moonharvest"
                getPlanetEvent().contains("Worm") -> "moonworm"
                getPlanetEvent().contains("Snow") -> "moonsnow"
                getPlanetEvent().contains("Cold") -> "mooncold"
                getPlanetEvent().contains("Corn") -> "mooncorn"
                getPlanetEvent().contains("Strawberry") -> "moonstrawberry"
                getPlanetEvent().contains("Wolf") -> "moonwolf"
                getPlanetEvent().contains("Sturgeon") -> "moonbanimal"
                getPlanetEvent().contains("Buck") -> "moonbanimal"
                getPlanetEvent().contains("Flower") -> "moonpink"
                getPlanetEvent().contains("Beaver") -> "moonbeaver"
                getPlanetEvent().contains("Solstice" )-> "sun"
                getPlanetEvent().contains("Equinox")-> "sun"
                getPlanetEvent().contains("solstice")-> "sun"
                getPlanetEvent().contains("equinox")-> "sun"
                getPlanetEvent().contains("Mercury")-> "mercury"
                getPlanetEvent().contains("Venus")-> "venus"
                getPlanetEvent().contains("Mars")-> "mars"
                getPlanetEvent().contains("Jupiter")-> "jupiter"
                getPlanetEvent().contains("Saturn")-> "saturn"
                getPlanetEvent().contains("Uranus")-> "uranus"
                getPlanetEvent().contains("Neptune")-> "neptune"
                getPlanetEvent().contains("Pluto")-> "pluto"
                getPlanetEvent().contains("Meteor")-> "shower"
                getPlanetEvent().contains("meteor")-> "shower"
                getPlanetEvent().contains("None")-> "none"
                else -> "none"
            }

            return planetType2
        }

        private fun getPlanetEventTYPE3(): String {

            val planetType3 = when{
                getPlanetEvent3().contains("moon") -> "moon"
                getPlanetEvent3().contains("Moon") -> "moon"
                getPlanetEvent3().contains("Solstice" )-> "sun"
                getPlanetEvent3().contains("Equinox")-> "sun"
                getPlanetEvent3().contains("solstice")-> "sun"
                getPlanetEvent3().contains("equinox")-> "sun"
                getPlanetEvent3().contains("Mercury")-> "mercury"
                getPlanetEvent3().contains("Venus")-> "venus"
                getPlanetEvent3().contains("Mars")-> "mars"
                getPlanetEvent3().contains("Jupiter")-> "jupiter"
                getPlanetEvent3().contains("Saturn")-> "saturn"
                getPlanetEvent3().contains("Uranus")-> "uranus"
                getPlanetEvent3().contains("Neptune")-> "neptune"
                getPlanetEvent3().contains("Pluto")-> "pluto"
                getPlanetEvent3().contains("Meteor")-> "sun"
                getPlanetEvent3().contains("meteor")-> "sun"
                getPlanetEvent3().contains("None")-> "none"
                else -> "none"
            }

            return planetType3
        }




        private fun getHoroscope(): String {

            val sdf2 = SimpleDateFormat("MMMM")
            val sdf3 = SimpleDateFormat("d")
            val d = Date()
            val monthOfYear: String = sdf2.format(d)
            val dayOfMonth: String = sdf3.format(d)

            val horoscopeString = when(monthOfYear){
                "January" -> if(Integer.parseInt(dayOfMonth) in 1..19){ "Capricorn" }
                else {"Aquarius" }
                "February" ->  if(Integer.parseInt(dayOfMonth) in 1..18 ){"Aquarius"}
                else {"Pisces"}
                "March" -> if(Integer.parseInt(dayOfMonth) in 1..20 ){"Pisces"}
                else{ "Aries"}
                "April" -> if(Integer.parseInt(dayOfMonth) in 1..19 ){"Aries"}
                else {"Taurus"}
                "May" -> {"Taurus"}
                "June" -> if(Integer.parseInt(dayOfMonth) in 1..20 ){"Gemini"}
                else{"Cancer"}
                "July" -> if(Integer.parseInt(dayOfMonth) in 1..22) {"Cancer"}
                else {"Leo"}
                "August" ->if(Integer.parseInt(dayOfMonth) in 1..22){ "Leo"}
                else {"Virgo"}
                "September" -> if(Integer.parseInt(dayOfMonth) in 1..22) {"Virgo"}
                else{"Libra"}
                "October" -> if(Integer.parseInt(dayOfMonth) in 1..22) {"Libra"}
                else {"Scorpio"}
                "November" ->if(Integer.parseInt(dayOfMonth) in 1..21) { "Scorpio"}
                else {"Sagittarius"}
                "December" -> if(Integer.parseInt(dayOfMonth) in 1..21) { "Sagittarius"}
                else{ "Capricorn"}
                else -> "Cancer" }
            return horoscopeString
        }
        private fun getDayorNight(): String {
            val sdf = SimpleDateFormat("k")
            val d = Date()
            val militaryTime: String = sdf.format(d)

            val timeTypeString = when (Integer.parseInt(militaryTime)){
                in 0..5 -> "Night"
                in 6..18 -> "Day"
                in 19..23 -> "Night"
                else-> "Night"
            }
            return timeTypeString
        }

        val frameTime = INTERACTIVE_UPDATE_RATE_MS


        private fun getAstrologyBackground(): Bitmap {
            val sdf2 = SimpleDateFormat("MMMM")
            val d = Date()
            val monthOfYear: String = sdf2.format(d)

            val backgroundBitmap: Bitmap =
                when ((mCalendar.timeInMillis % (8 * frameTime)) / frameTime) {
                    0L-> when (getHoroscope()) {
                        "Aquarius" -> BitmapFactory.decodeResource(resources, R.drawable.aquarius)
                        "Aries" -> BitmapFactory.decodeResource(resources, R.drawable.aries)
                        "Cancer" -> BitmapFactory.decodeResource(resources, R.drawable.cancer)
                        "Capricorn" -> BitmapFactory.decodeResource(resources, R.drawable.capricorn)
                        "Gemini" -> BitmapFactory.decodeResource(resources, R.drawable.gemini)
                        "Leo" -> BitmapFactory.decodeResource(resources, R.drawable.leo)
                        "Libra" -> BitmapFactory.decodeResource(resources, R.drawable.libra)
                        "Pisces" -> BitmapFactory.decodeResource(resources, R.drawable.pisces)
                        "Sagittarius" -> BitmapFactory.decodeResource(resources, R.drawable.sagitarius)
                        "Scorpio" -> BitmapFactory.decodeResource(resources, R.drawable.scorpio)
                        "Taurus" -> BitmapFactory.decodeResource(resources, R.drawable.taurus)
                        "Virgo" -> BitmapFactory.decodeResource(resources, R.drawable.virgo)
                        else -> BitmapFactory.decodeResource(resources, R.drawable.cancer) }
                    1L -> when (getDayorNight()){
                        "Day" -> BitmapFactory.decodeResource(resources, R.drawable.sun)
                        "Night" -> BitmapFactory.decodeResource(resources, R.drawable.plainmoon)
                        else -> BitmapFactory.decodeResource(resources, R.drawable.sun) }
                    2L->  BitmapFactory.decodeResource(resources, R.drawable.saturn)
                    3L -> when(getMoonPhase()){
                        "New Moon" -> BitmapFactory.decodeResource(resources, R.drawable.newmoon)
                        "Waxing Crescent Moon" -> BitmapFactory.decodeResource(resources, R.drawable.rightcrescent)
                        "Waxing Half Moon" -> BitmapFactory.decodeResource(resources, R.drawable.halfmoonright)
                        "Waxing Gibbous Moon" -> BitmapFactory.decodeResource(resources, R.drawable.gibright)
                        "Full Moon" -> BitmapFactory.decodeResource(resources, R.drawable.fulloon)
                        "Waning Gibbous Moon" -> BitmapFactory.decodeResource(resources, R.drawable.gibleft)
                        "Waning half Moon" -> BitmapFactory.decodeResource(resources, R.drawable.halfmoonleft)
                        "Waning Crescent Moon" -> BitmapFactory.decodeResource(resources, R.drawable.leftcrescent)
                        else-> BitmapFactory.decodeResource(resources, R.drawable.newmoon)
                    }
                    4L -> when(getPlanetEventTYPE()){
                        "moonanimal"-> BitmapFactory.decodeResource(resources, R.drawable.moonanimal)
                        "moonbeaver"-> BitmapFactory.decodeResource(resources, R.drawable.moonbeaver)
                        "mooncold"-> BitmapFactory.decodeResource(resources, R.drawable.mooncold)
                        "mooncorn"-> BitmapFactory.decodeResource(resources, R.drawable.mooncorn)
                        "moonharvest"-> BitmapFactory.decodeResource(resources, R.drawable.moonharvest)
                        "moonpink"-> BitmapFactory.decodeResource(resources, R.drawable.moonpink)
                        "moonsnow"-> BitmapFactory.decodeResource(resources, R.drawable.moonsnow)
                        "moonstrawberry"-> BitmapFactory.decodeResource(resources, R.drawable.moonwolf)
                        "moonworm"-> BitmapFactory.decodeResource(resources, R.drawable.moonworm)
                        "moonwolf"-> BitmapFactory.decodeResource(resources, R.drawable.moonbeaver)
                        "meteor"-> BitmapFactory.decodeResource(resources, R.drawable.shower)
                        "sun"-> BitmapFactory.decodeResource(resources, R.drawable.sun)
                        "mercury"-> BitmapFactory.decodeResource(resources, R.drawable.mercury)
                        "venus"-> BitmapFactory.decodeResource(resources, R.drawable.venus)
                        "mars"-> BitmapFactory.decodeResource(resources, R.drawable.mars)
                        "jupiter"-> BitmapFactory.decodeResource(resources, R.drawable.jupiter)
                        "saturn"-> BitmapFactory.decodeResource(resources, R.drawable.saturn)
                        "uranus"-> BitmapFactory.decodeResource(resources, R.drawable.uranus)
                        "neptune"-> BitmapFactory.decodeResource(resources, R.drawable.neptune)
                        "none" -> when(getPlanetEventTYPE3()){
                            "moon"-> BitmapFactory.decodeResource(resources, R.drawable.plainmoon)
                            "sun"-> BitmapFactory.decodeResource(resources, R.drawable.sun)
                            "mercury"-> BitmapFactory.decodeResource(resources, R.drawable.mercury)
                            "venus"-> BitmapFactory.decodeResource(resources, R.drawable.venus)
                            "mars"-> BitmapFactory.decodeResource(resources, R.drawable.mars)
                            "jupiter"-> BitmapFactory.decodeResource(resources, R.drawable.jupiter)
                            "saturn"-> BitmapFactory.decodeResource(resources, R.drawable.saturn)
                            "uranus"-> BitmapFactory.decodeResource(resources, R.drawable.uranus)
                            "neptune"-> BitmapFactory.decodeResource(resources, R.drawable.neptune)

                            else -> BitmapFactory.decodeResource(resources, R.drawable.sun)}
                        else -> BitmapFactory.decodeResource(resources, R.drawable.sun)}

                    5L -> when(getPlanetEventTYPE2()){
                        "moonanimal"-> BitmapFactory.decodeResource(resources, R.drawable.moonanimal)
                        "moonbeaver"-> BitmapFactory.decodeResource(resources, R.drawable.moonbeaver)
                        "mooncold"-> BitmapFactory.decodeResource(resources, R.drawable.mooncold)
                        "mooncorn"-> BitmapFactory.decodeResource(resources, R.drawable.mooncorn)
                        "moonharvest"-> BitmapFactory.decodeResource(resources, R.drawable.moonharvest)
                        "moonpink"-> BitmapFactory.decodeResource(resources, R.drawable.moonpink)
                        "moonsnow"-> BitmapFactory.decodeResource(resources, R.drawable.moonsnow)
                        "moonstrawberry"-> BitmapFactory.decodeResource(resources, R.drawable.moonwolf)
                        "moonworm"-> BitmapFactory.decodeResource(resources, R.drawable.moonworm)
                        "moonwolf"-> BitmapFactory.decodeResource(resources, R.drawable.moonbeaver)
                        "meteor"-> BitmapFactory.decodeResource(resources, R.drawable.shower)
                        "sun"-> BitmapFactory.decodeResource(resources, R.drawable.sun)
                        "mercury"-> BitmapFactory.decodeResource(resources, R.drawable.mercury)
                        "venus"-> BitmapFactory.decodeResource(resources, R.drawable.venus)
                        "mars"-> BitmapFactory.decodeResource(resources, R.drawable.mars)
                        "jupiter"-> BitmapFactory.decodeResource(resources, R.drawable.jupiter)
                        "saturn"-> BitmapFactory.decodeResource(resources, R.drawable.saturn)
                        "uranus"-> BitmapFactory.decodeResource(resources, R.drawable.uranus)
                        "neptune"-> BitmapFactory.decodeResource(resources, R.drawable.neptune)
                        "none" -> when(getPlanetEventTYPE3()){
                            "moon"-> BitmapFactory.decodeResource(resources, R.drawable.plainmoon)
                            "sun"-> BitmapFactory.decodeResource(resources, R.drawable.sun)
                            "mercury"-> BitmapFactory.decodeResource(resources, R.drawable.mercury)
                            "venus"-> BitmapFactory.decodeResource(resources, R.drawable.venus)
                            "mars"-> BitmapFactory.decodeResource(resources, R.drawable.mars)
                            "jupiter"-> BitmapFactory.decodeResource(resources, R.drawable.jupiter)
                            "saturn"-> BitmapFactory.decodeResource(resources, R.drawable.saturn)
                            "uranus"-> BitmapFactory.decodeResource(resources, R.drawable.uranus)
                            "neptune"-> BitmapFactory.decodeResource(resources, R.drawable.neptune)
                            else -> BitmapFactory.decodeResource(resources, R.drawable.sun)}
                        else -> BitmapFactory.decodeResource(resources, R.drawable.sun)}

                    6L -> when(getPlanetEventTYPE3()){
                        "moon"-> BitmapFactory.decodeResource(resources, R.drawable.plainmoon)
                        "sun"-> BitmapFactory.decodeResource(resources, R.drawable.sun)
                        "mercury"-> BitmapFactory.decodeResource(resources, R.drawable.mercury)
                        "venus"-> BitmapFactory.decodeResource(resources, R.drawable.venus)
                        "mars"-> BitmapFactory.decodeResource(resources, R.drawable.mars)
                        "jupiter"-> BitmapFactory.decodeResource(resources, R.drawable.jupiter)
                        "saturn"-> BitmapFactory.decodeResource(resources, R.drawable.saturn)
                        "uranus"-> BitmapFactory.decodeResource(resources, R.drawable.uranus)
                        "neptune"-> BitmapFactory.decodeResource(resources, R.drawable.neptune)
                        else -> BitmapFactory.decodeResource(resources, R.drawable.sun)}

                    7L -> when(monthOfYear){
                        "January"-> BitmapFactory.decodeResource(resources, R.drawable.moonwolf)
                        "February"-> BitmapFactory.decodeResource(resources, R.drawable.moonsnow)
                        "March"-> BitmapFactory.decodeResource(resources, R.drawable.moonworm)
                        "April"-> BitmapFactory.decodeResource(resources, R.drawable.moonpink)
                        "May"-> BitmapFactory.decodeResource(resources, R.drawable.moonpink)
                        "June"-> BitmapFactory.decodeResource(resources, R.drawable.moonstrawberry)
                        "July"-> BitmapFactory.decodeResource(resources, R.drawable.moonanimal)
                        "August"-> BitmapFactory.decodeResource(resources, R.drawable.moonanimal)
                        "September"-> BitmapFactory.decodeResource(resources, R.drawable.mooncorn)
                        "October"-> BitmapFactory.decodeResource(resources, R.drawable.moonharvest)
                        "November"-> BitmapFactory.decodeResource(resources, R.drawable.moonbeaver)
                        "December"-> BitmapFactory.decodeResource(resources, R.drawable.mooncold)
                        else -> BitmapFactory.decodeResource(resources, R.drawable.sun)}

                    else -> BitmapFactory.decodeResource(resources, R.drawable.cancer)
                }
            return backgroundBitmap
        }






        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}
