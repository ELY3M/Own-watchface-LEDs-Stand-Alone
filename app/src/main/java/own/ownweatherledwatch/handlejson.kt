package own.ownweatherledwatch

import kotlin.jvm.Volatile
import android.annotation.SuppressLint
import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.lang.Exception
import java.lang.NullPointerException
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection

class handlejson(url: String?) {
    private val agent = "my own written app email: elymbmx@gmail.com"
    var temp = "Temp"
        private set
    var weather = "Weather"
        private set
    var icon = "Weatherimage"
        private set
    private var urlString: String? = null

    @Volatile
    var parsingComplete = true
    @SuppressLint("NewApi")
    fun readAndParseJSON(`in`: String?) {
        try {
            val reader = JSONObject(`in`)
            val current = reader.getJSONObject("currentobservation")
            temp = current.getString("Temp")
            weather = current.getString("Weather")
            icon = current.getString("Weatherimage")
            parsingComplete = false
        } catch (e: Exception) {
            Log.i(TAG, "failed to readAndParseJSON(...)...")
            e.printStackTrace()
        }
    }

    fun fetchJSON() {
        val thread = Thread {
            try {
                val url = URL(urlString)
                val conn = url.openConnection() as HttpsURLConnection
                Log.d(TAG, agent)
                conn.setRequestProperty("User-Agent", agent)
                conn.setRequestProperty("Accept", "application/vnd.noaa.dwml+xml;version=1")
                conn.readTimeout = 10000
                conn.connectTimeout = 10000
                conn.requestMethod = "GET"
                conn.doInput = true
                // Starts the query
                conn.connect()
                val stream = conn.inputStream
                val data = convertStreamToString(stream)
                readAndParseJSON(data)
                stream.close()
            } catch (np: NullPointerException) {
                Log.i(TAG, "NullPointerException in fetchJSON()...")
                np.printStackTrace()
            } catch (io: IOException) {
                Log.i(TAG, "IOException in fetchJSON()...")
                io.printStackTrace()
            } catch (e: Exception) {
                Log.i(TAG, "Exception in fetchJSON()...")
                e.printStackTrace()
            }
        }
        Log.i(TAG, "fetchJSON() thread start")
        thread.start()
        Log.i(TAG, "fetchJSON() end...")
    }

    companion object {
        private const val TAG = "ownwatchface handlejson"
        fun convertStreamToString(`is`: InputStream?): String {
            val s = Scanner(`is`).useDelimiter("\\A")
            return if (s.hasNext()) s.next() else ""
        }
    }

    init {
        urlString = url
    }
}