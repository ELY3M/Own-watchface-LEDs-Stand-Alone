package own.ownweatherledwatch

import android.annotation.SuppressLint
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL
import java.util.concurrent.TimeUnit


//import javax.net.ssl.HttpsURLConnection

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
                val url = URL(urlString)
                /*
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

                 */

                val client: OkHttpClient = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .callTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false)
                    .build()


                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", agent)
                    .addHeader("Accept", "application/vnd.noaa.dwml+xml;version=1")
                    .get()
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        //e.printStackTrace()
                        val stacktrace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString().trim()
                        Log.e(TAG, "Exception occurred, stacktrace: " + stacktrace)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!response.isSuccessful) {
                                //throw IOException("Unexpected code $response")
                                Log.i(TAG, "Unexpected code $response")
                            }

                            for ((name, value) in response.headers) {
                                Log.i(TAG, "$name: $value")
                            }
                            readAndParseJSON(response.body!!.string())
                        }
                    }
                })

        }
        Log.i(TAG, "fetchJSON() thread start")
        thread.start()
        Log.i(TAG, "fetchJSON() end...")
    }

    companion object {
        private const val TAG = "ownweatherface handlejson"

        /*
        fun convertStreamToString(`is`: InputStream?): String {
            val s = Scanner(`is`).useDelimiter("\\A")
            return if (s.hasNext()) s.next() else ""
        }
        */

    }

    init {
        urlString = url
    }
}