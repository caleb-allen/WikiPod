package io.caleballen.wikipod

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.support.v7.app.AlertDialog
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.DialogOnAnyDeniedMultiplePermissionsListener
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

class MainActivity : AppCompatActivity() {
    var textToSpeech : TextToSpeech? = null
    var ttsIsInitialized = false
    val thingsToSay : Queue<String> = LinkedBlockingQueue()
    lateinit var httpClient : OkHttpClient
    lateinit var doc : Document

    lateinit var sensorManager : SensorManager
    lateinit var proximitySensor : Sensor

    lateinit var speechManager : SpeechManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        speechManager = SpeechManager(this, {handleCommand(it)})

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
            /*override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
//                view.loadUrl(url)
                return false
            }*/
        }
        val logger = HttpLoggingInterceptor()
        logger.level = HttpLoggingInterceptor.Level.BASIC
        httpClient = OkHttpClient.Builder()
                .addInterceptor(logger)
                .build()

        permissions()

    }

    fun handleCommand(s: String) {
        val words = s.split(" ")
        var query = ""
        words.forEach{
            query += it + "+"
        }
        if (query.length > 1) {
            query = query.substring(0, query.lastIndex)
        }
        val queryUrl = "https://en.m.wikipedia.org/w/index.php?search=$query"
        Timber.d(query)
        Timber.d(queryUrl)
//        webView.loadUrl(queryUrl)
        startTalking(queryUrl)
    }

    fun initialize(){
        say("Welcome to WikiPod!")
//        say("Welcome to WikiPod. Wave your hand in front of your phone to stop WikiPod. Wave it again to give a command. For additional help, wave your hand in front of your phone and say 'help'.")
    }

    fun startTalking(wikiUrl: String){
        getHtml(wikiUrl, {
            val html = it
            doc = Jsoup.parse(html)
            say(getPageTitle())
            say(getSummary())
            say("Page Contents")
            say(getContentsTitles())
//            say("Purchase WikiPod Premium to enable voice navigation")
        })
    }

    fun say(s : String){
        thingsToSay.add(s)
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(this, TextToSpeech.OnInitListener {
                if (it == TextToSpeech.SUCCESS) {
                    ttsIsInitialized = true
                    sayAll()
                }else if (it == TextToSpeech.ERROR) {
                    Timber.e("Error initializing TTS")
                }
            })
        }else if(ttsIsInitialized){
            sayAll()
        }
    }

    fun sayAll(){
        while (thingsToSay.isNotEmpty()) {
            val s = thingsToSay.poll()
            Timber.d("Saying: $s")
            textToSpeech?.speak(s, TextToSpeech.QUEUE_ADD, null)
        }
    }

    fun getHtml(url : String, callback: (String) -> Unit) {
        webView.loadUrl(url)
        getPage(url, callback)
    }

    val sensorListener = object : SensorEventListener{
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

        }

        override fun onSensorChanged(event: SensorEvent?) {
            val distance = event?.values!!.first()
            Timber.d(distance.toString())
            if (distance < proximitySensor.maximumRange) {
                if (textToSpeech != null) {
                    if (textToSpeech!!.isSpeaking) {
                        textToSpeech!!.stop()
                    }else{
                        speechManager.listen()
                    }
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(sensorListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        sensorManager.unregisterListener(sensorListener)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun getPageTitle() : String{
        val element = doc.select("#section_0")
        return element.text()
    }

    fun getSummary() : String{
        val element = doc.select("#mf-section-0")
        element.select("span").remove()
        element.select(".thumb").remove()
        element.select(".hatnote").remove()
        element.select("h2").remove()
        element.select("table").remove()
        val text = element.text()
        return text.stripNonProunouncable()
    }

    fun getContentsTitles(): String {
        val elements = doc.select("h2 .mw-headline")
        var s : String = ""
        elements.eachText().forEach {
            s += it + ". "
        }
        return s
    }

    fun String.stripNonProunouncable() : String{
        val pairs = hashMapOf<Char, Char>(
                '(' to ')',
                '{' to '}',
                '[' to ']'
        )

        val builder = StringBuilder()
        var closer : Char? = null
        for (c in this) {
            if (pairs.containsKey(c)) {
                closer = pairs[c]
            }
            if (closer == null) {
                builder.append(c)
            }
            if(c == closer){
                closer = null
            }
        }
        return builder.toString()
    }

    fun getPage(url : String, callback: (String) -> Unit) {
        val request = Request.Builder()
                .url(url)
                .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call?, response: Response?) {
                if (response != null && response.isSuccessful) {
                    runOnUiThread {
                        callback(response.body()!!.string())
                    }
                }
            }

            override fun onFailure(call: Call?, e: IOException?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        })
    }


    fun permissions(){
        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.RECORD_AUDIO
                ).withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            initialize()
                        }else{
                            AlertDialog.Builder(this@MainActivity)
                                    .setMessage("Permissions are required to run this application.")
                                    .setPositiveButton(android.R.string.ok) { dialog, which ->
                                        permissions()
                                    }
                                    .show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>?, token: PermissionToken?) {
                        token?.continuePermissionRequest()
                    }
                })
                .check()
    }
}
