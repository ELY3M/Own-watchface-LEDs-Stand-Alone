package own.ownweatherledwatch

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.text.format.DateFormat
import android.util.Log
import android.view.SurfaceHolder
import com.intentfilter.androidpermissions.PermissionManager
import com.intentfilter.androidpermissions.PermissionManager.PermissionRequestListener
import com.intentfilter.androidpermissions.models.DeniedPermissions
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import java.util.Collections.singleton
import java.util.regex.Pattern
import kotlin.math.roundToInt
import kotlinx.coroutines.*

/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

val TAG = "ownweatherwatch"

var width = 0
var height = 0

var roundwatch = false
var havegps = false

private const val MOON_PHASE_LENGTH = 29.530588853
private var moonCalendar: Calendar? = null

val refreshTime = 30 //30 mins is enough
//private const val url = "https://testing/MapClick.php?"
private const val url = "https://forecast.weather.gov/MapClick.php?"
private var finalurl = "setup"
private var visiturl = "setup"
private var obj: handlejson? = null

private lateinit var locationManager: LocationManager
private var mylocation: Location? = null
private var lat = 0.0
private var lon = 0.0
private var temp = "NA°F"
private var icon = "unknown"
private var finalicon = "unknown"
private var weather = "unknown"

var dateString: SimpleDateFormat? = null
var timeString: SimpleDateFormat? = null
var timenosecsString: SimpleDateFormat? = null
var periodString: SimpleDateFormat? = null
var timeStampString: SimpleDateFormat? = null

var DatePaint: Paint? = null
var ClockPaint: Paint? = null
var ClocknosecsPaint: Paint? = null
var PeriodPaint: Paint? = null
var TimestampPaint: Paint? = null
var TempPaint: Paint? = null
var WeatherPaint: Paint? = null

var clockSize = 26
var clocknosecsSize = 43
var markerSize = 18
var dateSize = 18
var timeSize = 18
var tempSize = 30
var weatherSize = 13

var Padding = 5f
var temponright = false

var showtimestamp = false

class MyWatchFace : CanvasWatchFaceService(), LocationListener  {

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: MyWatchFace.Engine) : Handler(Looper.myLooper()!!) {
        private val mWeakReference: WeakReference<MyWatchFace.Engine> = WeakReference(reference)

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


        var BOLD_TYPEFACE = Typeface.createFromAsset(resources.assets, "fonts/DS-DIGIB.TTF")
        var NORMAL_TYPEFACE = Typeface.createFromAsset(resources.assets, "fonts/DS-DIGI.TTF")
        var ITALIC_TYPEFACE = Typeface.createFromAsset(resources.assets, "fonts/DS-DIGIT.TTF")
        var PIXELLCD = Typeface.createFromAsset(resources.assets, "fonts/PixelLCD-7.ttf")

        private val DATE_FORMAT = "EEE MMM, dd yyyy"
        private val TIME_FORMAT_12_NOSECS = "h:mm"
        private val TIME_FORMAT_12 = "h:mm:ss"
        private val TIME_FORMAT_24_NOSECS = "H:mm"
        private val TIME_FORMAT_24 = "H:mm:ss"
        private val PERIOD_FORMAT = "a"
        private val TIMESTAMP_FORMAT = "HH:mm:ss zzz"
        private val TIMEZONE_FORMAT = "zzz"


        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            fun hasGps(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
            roundwatch = this@MyWatchFace.getResources().getConfiguration().isScreenRound()
            Log.i(TAG, "roundwatch: "+ roundwatch)

            //check if this watch has gps or not
            if (!hasGps()) {
                havegps = false
                Log.d(TAG, "This hardware doesn't have GPS. :(")
            } else {
                havegps = true
                Log.d(TAG, "Great!!! This hardware do have GPS. :)")


                //location permission
                val permissionManager = PermissionManager.getInstance(applicationContext)
                permissionManager.checkPermissions(singleton(Manifest.permission.ACCESS_FINE_LOCATION), object : PermissionRequestListener {
                    override fun onPermissionGranted() {
                        Log.d(TAG, "Great!!! Got Perms!. :)")
                        getLocation()
                    }

                    override fun onPermissionDenied(deniedPermissions: DeniedPermissions) {
                        Log.d(TAG, "Permissions Denied")

                    }
                })

            }


            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()


            if (DateFormat.is24HourFormat(applicationContext)) {
                timeString = SimpleDateFormat(TIME_FORMAT_24)
                timenosecsString =
                    SimpleDateFormat(TIME_FORMAT_24_NOSECS)
            } else {
                timeString = SimpleDateFormat(TIME_FORMAT_12)
                timenosecsString =
                    SimpleDateFormat(TIME_FORMAT_12_NOSECS)
            }

            //date formatters
            dateString = SimpleDateFormat(DATE_FORMAT)
            periodString = SimpleDateFormat(PERIOD_FORMAT)
            timeStampString = SimpleDateFormat(TIMESTAMP_FORMAT)

            //set this time to UTC
            timeStampString!!.setTimeZone(SimpleTimeZone(0, "UTC"))

            DatePaint = createTextPaint(getResources().getColor(R.color.aqua), NORMAL_TYPEFACE)
            ClockPaint = createTextPaint(getResources().getColor(R.color.aqua), PIXELLCD)
            ClocknosecsPaint = createTextPaint(getResources().getColor(R.color.aqua), PIXELLCD)
            PeriodPaint = createTextPaint(getResources().getColor(R.color.aqua), PIXELLCD)
            TimestampPaint = createTextPaint(getResources().getColor(R.color.aqua), NORMAL_TYPEFACE)
            TempPaint = createTextPaint(getResources().getColor(R.color.aqua), NORMAL_TYPEFACE)
            WeatherPaint = createTextPaint(getResources().getColor(R.color.aqua), NORMAL_TYPEFACE)


            // set the text sizes scaled according to the screen density
            val density = resources.displayMetrics.density
            DatePaint!!.setTextSize(dateSize * density)
            ClockPaint!!.setTextSize(clockSize * density)
            ClocknosecsPaint!!.setTextSize(clocknosecsSize * density)
            PeriodPaint!!.setTextSize(markerSize * density)
            TimestampPaint!!.setTextSize(timeSize * density)
            TempPaint!!.setTextSize(tempSize * density)
            WeatherPaint!!.setTextSize(weatherSize * density)

            initializeBackground()
            initializeWatchFace()
        }

        private fun initializeBackground() {
            mBackgroundPaint = Paint().apply {
                color = Color.BLACK
            }
            mBackgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.back)

        }

        private fun initializeWatchFace() {
            /* Set defaults for colors */

        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false
            )
            mBurnInProtection = properties.getBoolean(
                WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false
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

            } else {

            }
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode


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

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP -> {
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    //getLocation()
                    //weatherupdate()
                    //Log.i(TAG, "Tapped - should show GPS Info and Weather Updated")
                    //manual update weather
                    if (x > 3 && y > 143 && y < 263) {
                        Log.d(TAG, "onTapCommand")
                        getLocation()
                        if (isRefreshNeeded()) {
                            Log.i(TAG, "onTapCommand weatherupate() - time to refresh")
                            weatherupdate()
                        }
                        Log.i(TAG, "Tapped - should show GPS Info and Weather Updated")
                    }

                    //x 30-width
                    //y 263-height
                    // toggle UTC display
                    if (x > 3 && y > 263) {
                        Log.d(TAG, "onTapCommand toggle UTC display")
                        if (!showtimestamp) {
                            Log.d(TAG, "Tap showtime is true")
                            showtimestamp = true
                        } else {
                            Log.d(TAG, "Tap showtime is false")
                            showtimestamp = false
                        }
                    }


                }
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            width = bounds.width()
            height = bounds.height()


            DatePaint!!.setTextAlign(Paint.Align.CENTER)
            ClockPaint!!.setTextAlign(Paint.Align.LEFT)
            ClocknosecsPaint!!.setTextAlign(Paint.Align.CENTER)
            PeriodPaint!!.setTextAlign(Paint.Align.RIGHT)
            TimestampPaint!!.setTextAlign(Paint.Align.CENTER)
            TempPaint!!.setTextAlign(Paint.Align.CENTER)
            WeatherPaint!!.setTextAlign(Paint.Align.CENTER)


            // Update the strings
            var dateString = dateString!!.format(now)
            var clockString = timeString!!.format(now)
            var clocknosecsString = timenosecsString!!.format(now)
            var periodString = periodString!!.format(now)
            var timestampString = timeStampString!!.format(now)
            var TempString = temp
            var WeatherString = weather

            //Log.d(TAG, "onDraw dateString: $dateString")
            //Log.d(TAG, "onDraw Temp: $temp")
            //Log.d(TAG, "onDraw icon: $icon")
            //Log.d(TAG, "onDraw Weather: $weather")

            val xClock: Float
            val yClock: Float
            val xClocknosecs: Float
            val yClocknosecs: Float
            val xPeriod: Float
            val yPeriod: Float
            val xDatestamp: Float
            val yDatestamp: Float
            val xTimestamp: Float
            val yTimestamp: Float
            val xTemp: Float
            val yTemp: Float
            val xWeather: Float
            val yWeather: Float


            if (!roundwatch) {
                xDatestamp = width / 2f
                yDatestamp = 25f
            } else {
                xDatestamp = width / 2f
                yDatestamp = 75f
            }

            xPeriod = width - Padding
            yPeriod = height / 2f

            ///xClock = width / 2f;

            ///xClock = width / 2f;
            xClock = Padding
            yClock = height / 2f
            xClocknosecs = width / 2f - 30
            yClocknosecs = height / 2f

            xTimestamp = width / 2f

            yTimestamp = if (!roundwatch) {
                (height - 4).toFloat()
            } else {
                (height - 18).toFloat()
            }

            if (!roundwatch) {
                if (temponright) {
                    xTemp = width / 2f + 90
                    yTemp = (height - 80).toFloat()
                } else {
                    xTemp = 50f
                    yTemp = (height - 80).toFloat()
                }
            } else {
                if (temponright) {
                    xTemp = width / 2f + 120
                    yTemp = (height - 100).toFloat() //was 130
                } else {
                    xTemp = 93f
                    yTemp = (height - 100).toFloat() //was 130
                }
            }
            xWeather = width / 2f
            yWeather = if (!roundwatch) {
                (height - 30).toFloat()
            } else {
                height / 2f + 160
            }




            drawBackground(canvas)
            drawWatchFace(canvas)
            moonupdate(canvas)
            canvas.drawText(dateString, xDatestamp, yDatestamp, DatePaint!!)
            clockleds(canvas, bounds)
            canvas.drawText(TempString, xTemp, yTemp, TempPaint!!)
            weathericon(canvas)
            canvas.drawText(WeatherString, xWeather, yWeather, WeatherPaint!!)
            if (showtimestamp) {
                canvas.drawText(timestampString, xTimestamp, yTimestamp, TimestampPaint!!)
            }




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



            canvas.save()


            if (!mAmbient) {

            }


            /* Restore the canvas" original orientation. */
            canvas.restore()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren"t visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                getLocation()
                if (isRefreshNeeded()) {
                    Log.i(TAG, "weatherupate() on watchface visible - time to refresh")
                    weatherupdate()
                }
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


    fun createTextPaint(color: Int, typeface: Typeface?): Paint? {
        val paint = Paint()
        paint.color = color
        if (typeface != null) paint.typeface = typeface
        paint.isAntiAlias = true
        return paint
    }

/////GPS Stuff//////
    private fun getLocation() {
    locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    locationManager.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 5000, 5f, this)

    mylocation = locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)

    if (mylocation != null) {
        lat = mylocation!!.latitude
        lon = mylocation!!.longitude
        Log.d(TAG, "Lat: " + lat)
        Log.d(TAG, "Lon: " + lon)

    } else {
        Log.d(TAG, "mylocation is null :(")
    }


}

    override fun onLocationChanged(location: Location) {
        mylocation = location
        lat = mylocation!!.latitude
        lon = mylocation!!.longitude
        Log.d(TAG, "Lat: "+ lat)
        Log.d(TAG, "Lon: "+ lon)
    }
/////end of gps stuff/////





    /////moon stuff/////
    fun moonupdate(canvas: Canvas) {
        val resources: Resources = this.getResources()
        val mMoonBitmap: Bitmap
        val mMoonResizedBitmap: Bitmap
        val northernhemi: Boolean = isNorthernHemi()

        val phase = computeMoonPhase()
        Log.i("0wnmoon", "Computed moon phase: $phase")
        val phaseValue = Math.floor(phase).toInt() % 30
        Log.i("0wnmoon", "Discrete phase value: $phaseValue")
        val moonDrawable = resources.getDrawable(IMAGE_LOOKUP[phaseValue])
        var moonleft = 23
        var moontop = 33

        if (roundwatch) {
            moonleft = 75
            moontop = 90
        }

        if (northernhemi) {
            mMoonBitmap = (moonDrawable as BitmapDrawable).bitmap
            mMoonResizedBitmap = Bitmap.createScaledBitmap(mMoonBitmap, 73, 73, false)
            canvas.drawBitmap(mMoonResizedBitmap, moonleft.toFloat(), moontop.toFloat(), null)
        } else {
            val matrix = Matrix()
            matrix.postRotate(180f)
            mMoonBitmap = (moonDrawable as BitmapDrawable).bitmap
            mMoonResizedBitmap = Bitmap.createScaledBitmap(mMoonBitmap, 73, 73, false)
            val mMoonrotatedBitmap = Bitmap.createBitmap(
                mMoonResizedBitmap,
                0,
                0,
                mMoonResizedBitmap.width,
                mMoonResizedBitmap.height,
                matrix,
                true
            )
            canvas.drawBitmap(mMoonrotatedBitmap, moonleft.toFloat(), moontop.toFloat(), null)
        }
    }

    /// not all fucking watches have gps :(
    private fun isNorthernHemi(): Boolean {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val location = locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
        return if (location != null) {
            location.longitude > 0
        } else false
    }


    // Computes moon phase based upon Bradley E. Schaefer's moon phase algorithm.
    private fun computeMoonPhase(): Double {
        moonCalendar = Calendar.getInstance()
        val year: Int = moonCalendar!!.get(Calendar.YEAR)
        val month: Int = moonCalendar!!.get(Calendar.MONTH) + 1
        val day: Int = moonCalendar!!.get(Calendar.DAY_OF_MONTH)
        val minute: Int = moonCalendar!!.get(Calendar.MINUTE) ///for testing///
        Log.i("0wnmoon", "year: $year month: $month day: $day")
        // Convert the year into the format expected by the algorithm.
        val transformedYear = year - Math.floor(((12 - month) / 10).toDouble())
        Log.i("0wnmoon", "transformedYear: $transformedYear")

        // Convert the month into the format expected by the algorithm.
        var transformedMonth = month + 9
        if (transformedMonth >= 12) {
            transformedMonth = transformedMonth - 12
        }
        Log.i("0wnmoon", "transformedMonth: $transformedMonth")

        // Logic to compute moon phase as a fraction between 0 and 1
        val term1 = Math.floor(365.25 * (transformedYear + 4712))
        val term2 = Math.floor(30.6 * transformedMonth + 0.5)
        val term3 = Math.floor(Math.floor(transformedYear / 100 + 49) * 0.75) - 38
        var intermediate = term1 + term2 + day + 59
        if (intermediate > 2299160) {
            intermediate = intermediate - term3
        }
        Log.i("0wnmoon", "intermediate: $intermediate")
        var normalizedPhase: Double =
            (intermediate - 2451550.1) / MOON_PHASE_LENGTH
        normalizedPhase = normalizedPhase - Math.floor(normalizedPhase)
        if (normalizedPhase < 0) {
            normalizedPhase = normalizedPhase + 1
        }
        Log.i("0wnmoon", "normalizedPhase: $normalizedPhase")

        // Return the result as a value between 0 and MOON_PHASE_LENGTH
        return normalizedPhase * MOON_PHASE_LENGTH
    }

    private val IMAGE_LOOKUP = intArrayOf(
        R.drawable.moon0,
        R.drawable.moon1,
        R.drawable.moon2,
        R.drawable.moon3,
        R.drawable.moon4,
        R.drawable.moon5,
        R.drawable.moon6,
        R.drawable.moon7,
        R.drawable.moon8,
        R.drawable.moon9,
        R.drawable.moon10,
        R.drawable.moon11,
        R.drawable.moon12,
        R.drawable.moon13,
        R.drawable.moon14,
        R.drawable.moon15,
        R.drawable.moon16,
        R.drawable.moon17,
        R.drawable.moon18,
        R.drawable.moon19,
        R.drawable.moon20,
        R.drawable.moon21,
        R.drawable.moon22,
        R.drawable.moon23,
        R.drawable.moon24,
        R.drawable.moon25,
        R.drawable.moon26,
        R.drawable.moon27,
        R.drawable.moon28,
        R.drawable.moon29
    )
////  end of moon stuff /////


    //////clockleds/////////
    fun clockleds(canvas: Canvas, bounds: Rect) {
        val width = bounds.width()
        val height = bounds.height()
        val centerX = width.toFloat() / 2.0f
        val centerY = height.toFloat() / 2.0f
        var setclock = 3.0f
        var clockspace = 30
        var clockwidth = 43
        var clockheight = 53
        var colonwidth = 28
        var clocktop = 175.0f
        val resources: Resources = getResources()
        val textTime = SimpleDateFormat("hh:mm:ss").format(Date())
        val ampm = Calendar.getInstance()[Calendar.AM_PM]
        val logstring = "$textTime $ampm"
        Log.i("0wnleds","my screen size: width: $width height: $height"
        )
        Log.i("0wnleds", "My Clock Image built with $logstring")

        val clearBitmap = (resources.getDrawable(R.drawable.nc) as BitmapDrawable).bitmap
        val resizeclearBitmap = Bitmap.createScaledBitmap(clearBitmap, clockspace, clockheight, false)
        if (Character.getNumericValue(textTime[0]) == 1) {
            canvas.drawBitmap(resizeclearBitmap, setclock, clocktop, null)
        } else {
            setclock = (-(clearBitmap.width / 2)).toFloat()
        }
        Log.i("0wnleds", "bitmap width: " + clearBitmap.width)

        setclock = setclock + resizeclearBitmap.width

        Log.i("0wnleds","setclock: $setclock")

        val hour1Bitmap = (resources.getDrawable(
            CLOCKNUMBERS[Character.getNumericValue(
                textTime[0]
            )]
        ) as BitmapDrawable).bitmap
        val resizehour1Bitmap = Bitmap.createScaledBitmap(hour1Bitmap, clockwidth, clockheight, false)
        if (Character.getNumericValue(textTime[0]) == 1) {
            canvas.drawBitmap(resizehour1Bitmap, setclock, clocktop, null)
        } else {
            setclock = (-(hour1Bitmap.width / 2)).toFloat()
        }
        Log.i("0wnleds", "bitmap width: " + hour1Bitmap.width)
        val resizehour2Bitmap = Bitmap.createScaledBitmap(
            (resources.getDrawable(
                CLOCKNUMBERS[Character.getNumericValue(
                    textTime[1]
                )]
            ) as BitmapDrawable).bitmap, clockwidth, clockheight, false
        )
        canvas.drawBitmap(
            resizehour2Bitmap,
            resizehour1Bitmap.width.toFloat() + setclock,
            clocktop,
            null
        )
        val resizecolonBitmap = Bitmap.createScaledBitmap(
            (resources.getDrawable(R.drawable.dot) as BitmapDrawable).bitmap,
            colonwidth,
            clockheight,
            false
        )
        canvas.drawBitmap(
            resizecolonBitmap,
            resizehour1Bitmap.width.toFloat() + setclock + resizehour2Bitmap.width
                .toFloat(),
            clocktop,
            null
        )
        val resizeminute1Bitmap = Bitmap.createScaledBitmap(
            (resources.getDrawable(
                CLOCKNUMBERS[Character.getNumericValue(
                    textTime[3]
                )]
            ) as BitmapDrawable).bitmap, clockwidth, clockheight, false
        )
        canvas.drawBitmap(
            resizeminute1Bitmap,
            resizehour1Bitmap.width.toFloat() + setclock + resizehour2Bitmap.width
                .toFloat() + resizecolonBitmap.width.toFloat(),
            clocktop,
            null
        )
        val resizeminute2Bitmap = Bitmap.createScaledBitmap(
            (resources.getDrawable(
                CLOCKNUMBERS[Character.getNumericValue(
                    textTime[4]
                )]
            ) as BitmapDrawable).bitmap, clockwidth, clockheight, false
        )
        canvas.drawBitmap(
            resizeminute2Bitmap,
            resizehour1Bitmap.width.toFloat() + setclock + resizehour2Bitmap.width
                .toFloat() + resizecolonBitmap.width.toFloat() + resizeminute1Bitmap.width.toFloat(),
            clocktop,
            null
        )
        canvas.drawBitmap(
            resizecolonBitmap,
            resizehour1Bitmap.width.toFloat() + setclock + resizehour2Bitmap.width
                .toFloat() + resizecolonBitmap.width.toFloat() + resizeminute1Bitmap.width.toFloat() + resizeminute2Bitmap.width
                .toFloat(),
            clocktop,
            null
        )
        val resizeseconds1Bitmap = Bitmap.createScaledBitmap(
            (resources.getDrawable(
                CLOCKNUMBERS[Character.getNumericValue(
                    textTime[6]
                )]
            ) as BitmapDrawable).bitmap, clockwidth, clockheight, false
        )
        canvas.drawBitmap(
            resizeseconds1Bitmap,
            resizehour1Bitmap.width.toFloat() + setclock + resizehour2Bitmap.width
                .toFloat() + resizecolonBitmap.width.toFloat() + resizeminute1Bitmap.width.toFloat() + resizeminute2Bitmap.width
                .toFloat() + resizecolonBitmap.width.toFloat(),
            clocktop,
            null
        )
        val resizeseconds2Bitmap = Bitmap.createScaledBitmap(
            (resources.getDrawable(
                CLOCKNUMBERS[Character.getNumericValue(
                    textTime[7]
                )]
            ) as BitmapDrawable).bitmap, clockwidth, clockheight, false
        )
        canvas.drawBitmap(
            resizeseconds2Bitmap,
            resizehour1Bitmap.width.toFloat() + setclock + resizehour2Bitmap.width
                .toFloat() + resizecolonBitmap.width.toFloat() + resizeminute1Bitmap.width.toFloat() + resizeminute2Bitmap.width
                .toFloat() + resizecolonBitmap.width.toFloat() + resizeseconds1Bitmap.width.toFloat(),
            clocktop,
            null
        )
        canvas.drawBitmap(
            Bitmap.createScaledBitmap(
                (resources.getDrawable(AMPM[ampm]) as BitmapDrawable).bitmap,
                clockwidth,
                clockheight,
                false
            ), resizehour1Bitmap.width
                .toFloat() + setclock + resizehour2Bitmap.width.toFloat() + resizecolonBitmap.width
                .toFloat() + resizeminute1Bitmap.width.toFloat() + resizeminute2Bitmap.width
                .toFloat() + resizecolonBitmap.width.toFloat() + resizeseconds1Bitmap.width.toFloat() + resizeseconds2Bitmap.width
                .toFloat(), clocktop, null
        )
    }

    private val CLOCKNUMBERS = intArrayOf(
        R.drawable.n0,
        R.drawable.n1,
        R.drawable.n2,
        R.drawable.n3,
        R.drawable.n4,
        R.drawable.n5,
        R.drawable.n6,
        R.drawable.n7,
        R.drawable.n8,
        R.drawable.n9
    )

    private val AMPM = intArrayOf(
        R.drawable.am,
        R.drawable.pm
    )
//////////end of watch leds///////////////////


/////weather////

    fun weathericon(canvas: Canvas) {
        val resources: Resources = this.getResources()
        val mIconBitmap: Bitmap
        val mIconResizedBitmap: Bitmap
        Log.i(TAG,"in weathericon() Temp: $temp icon: $icon Weather: $weather"
        )
        val res = getResources().getIdentifier(finalicon, "drawable", packageName)
        //Log.i(TAG,"in weathericon() getPackageName(): $packageName")
        //Log.i(TAG, "in weathericon() res: $res")
        val IconDrawable = resources.getDrawable(res)
        mIconBitmap = (IconDrawable as BitmapDrawable).bitmap
        mIconResizedBitmap = if (!roundwatch) {
            Bitmap.createScaledBitmap(mIconBitmap, 100, 70, false)
        } else {
            Bitmap.createScaledBitmap(mIconBitmap, 150, 120, false)
        }
        if (!roundwatch) {
            canvas.drawBitmap(mIconResizedBitmap, 90f, 160f, null)
        } else {
            canvas.drawBitmap(mIconResizedBitmap, width / 2f - 75, height / 2f + 18, null)
        }
    }


//////////////////////
/// weather stuff///
fun weatherupdate() = GlobalScope.launch(Dispatchers.Main) {
    ///get weather////
    Log.i(TAG, "weatherupdate() started")
    finalurl = url + "lat=" + lat + "&lon=" + lon + "&FcstType=json"
    visiturl = url + "lat=" + lat + "&lon=" + lon
    Log.i(TAG, "finalurl: $finalurl")
    if (isOnline(applicationContext)) {
            obj = handlejson(finalurl)
            obj!!.fetchJSON()
            while (obj!!.parsingComplete);
            temp = obj!!.temp
            icon = obj!!.icon
            weather = obj!!.weather
            Log.i(TAG, "temp: $temp")
            Log.i(TAG, "icon: $icon")
            Log.i(TAG, "weather: $weather")
            val pattern = Pattern.compile("(.*?)(.png|.jpg|.gif)")
            val geticon = pattern.matcher(icon)
            while (geticon.find()) {
                finalicon = geticon.group(1)
            }

            if (temp == "NA") {
                temp = "NA°F"
            } else {

                val finaltemp = temp.toDouble().roundToInt()
                temp = "$finaltemp°F"
            }

            if (weather == "") {
                weather = "unknown (NA)"
            }

            Log.i(TAG, "finaltemp: $temp")

            Log.i(TAG, "icon: $icon")
            Log.i(TAG, "finalicon: $finalicon")


    } else { //internet check
        Log.i(TAG, "Failed Update (Offline)")
    }
}


    ///Simple online checker///
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = cm.activeNetworkInfo
        return if (netInfo != null && netInfo.isConnected) {
            Log.i(TAG, "network state = true")
            true
        } else {
            Log.i(TAG, "network state = false")
            false
        }
    }

///Thank you to Joshua Tee for this refresh code.
    private var initialized = false
    private var lastRefresh = 0.toLong()
    fun isRefreshNeeded(): Boolean {
        var refreshDataInMinutes = refreshTime
        var refreshNeeded = false
        val currentTime = System.currentTimeMillis()
        val currentTimeSeconds = currentTime / 1000
        val refreshIntervalSeconds = refreshDataInMinutes * 60
        if ((currentTimeSeconds > (lastRefresh + refreshIntervalSeconds)) || !initialized) {
            refreshNeeded = true
            initialized = true
            lastRefresh = currentTime / 1000
        }
        Log.d(TAG, "TIMER: $refreshNeeded")
        return refreshNeeded
    }

    fun resetTimer() {
        lastRefresh = 0
    }


}