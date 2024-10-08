package io.caleballen.wikipod.legacy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import com.google.gson.Gson
import io.caleballen.wikipod.R
import io.caleballen.wikipod.data.WikiGeoSearch
import io.caleballen.wikipod.speech.SpeechManager
import io.caleballen.wikipod.util.getPermission
import kotlinx.android.synthetic.main.legacy_activity_main.*
import kotlinx.android.synthetic.main.item_list_option.view.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {
    var textToSpeech: TextToSpeech? = null
    var ttsIsInitialized = false
    var ttsIsSpeaking = false
        set(value) {
            field = value
            runOnUiThread{
                imgStop.visibility = if (value) View.VISIBLE else View.GONE
            }
        }
    val thingsToSay: Queue<String> = LinkedBlockingQueue<String>()
    lateinit var httpClient: OkHttpClient
    lateinit var doc: Document

    lateinit var speechManager: SpeechManager
    lateinit var locationManager: LocationManager

    // list for adapter. First is label, second is action
    val listOptions = ArrayList<Pair<String, () -> Unit>>()
    val listAdapter = object : BaseAdapter() {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            var cv = convertView
            if (cv == null) {
                cv = LayoutInflater.from(this@MainActivity)
                        .inflate(R.layout.item_list_option, parent, false)
            }
            val v = cv!!
            v.textOption.text = "${position + 1} - ${listOptions[position].first}"
            v.textOption.setOnClickListener { listOptions[position].second() }
            return v
        }

        override fun getItem(position: Int): Any {
            return listOptions[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return listOptions.size
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.legacy_activity_main)

        speechManager = SpeechManager(this, { handleCommand(it) })
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        imgHelp.setOnClickListener {
            val i = Intent(this, HelpActivity::class.java)
            startActivity(i)
        }

        imgStop.setOnClickListener({
            textToSpeech?.stop()
            ttsIsSpeaking = false
        })

        val logger = HttpLoggingInterceptor()
        logger.level = HttpLoggingInterceptor.Level.BASIC
        httpClient = OkHttpClient.Builder()
                .addInterceptor(logger)
                .build()

        listViewOptions.adapter = listAdapter
    }

    fun handleCommand(s: String) {
        val eventBundle = Bundle()
        if (s.contains("nearby") || s.contains("what was that", ignoreCase = true)) {
            /*startTalking("https://en.m.wikipedia.org/wiki/Special:Nearby", s)*/
            getPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    "WikiPod requires access to your location in order to" +
                            " find items nearby.",
                    "WikiPod is unable to find nearby items without permission. " +
                            "Please accept the location permission in order to use this feature.",
                    {nearby()}
            )
            return
        }
//        val number = s.equals("one", true) ?: s.toIntOrNull()
        val number = if (s.equals("one", true)) {
            1
        } else if (s.equals("two", true)) {
            2
        } else {
            s.toIntOrNull()
        }
        if (number != null && listOptions.isNotEmpty()) {
            // we can select one of the options
            if (number > 0 && number - 1 <= listOptions.size) {
                listOptions[number - 1].second()
            } else {
                say("There is no option for $s")
            }
            return
        }

        if (s.equals("read it", true)) {
            if (listOptions.isNotEmpty()) {
                listOptions.forEachIndexed({ index: Int, pair: Pair<String, () -> Unit> ->
                    say("${index + 1}: ${pair.first}".stripNonProunouncable())
                })
            } else {
                say("There is nothing on the screen to dictate.")
            }
            return
        }

        if (s.equals("help", true)) {
            say("Help. Wave your hand in front of your phone to give WikiPod a command. Wave it again to stop WikiPod.")
            say("You can ask WikiPod for specific articles. For example, try saying 'Yellowstone'.")
            say("You can also ask WikiPod to find things near you. To do this, say 'what was that', or 'nearby'")
            say("WikiPod will sometimes display a list of options. Choose an option by clicking it or by saying its number. Hear all the options by saying 'read it'")
            say("Click the help button on the top right corner for more information, or say 'help' to hear this message again.")
            return
        }
        val words = s.split(" ")
        var query = ""
        words.forEach {
            query += it + "+"
        }
        if (query.length > 1) {
            query = query.substring(0, query.lastIndex)
        }
        val queryUrl = "https://en.m.wikipedia.org/w/index.php?search=$query"
        Timber.d(query)
        Timber.d(queryUrl)
        startTalking(queryUrl, s)
    }

    fun nearby() {
        val lastKnownLocation: Location?
        try {
            lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (e: SecurityException) {
            Toast.makeText(this, "An error occurred while fetching your location.", Toast.LENGTH_SHORT).show()
            return
        }
        val url = HttpUrl.Builder()
                .scheme("https")
                .host("en.wikipedia.org")
                .encodedPath("/w/api.php")
                .addQueryParameter("action", "query")
                .addQueryParameter("list", "geosearch")
                .addQueryParameter("gsradius", "10000")
                .addQueryParameter("gscoord", lastKnownLocation?.latitude.toString() + "|" +
                        lastKnownLocation?.longitude.toString())
                .addQueryParameter("format", "json")
                .build()
        val request = Request.Builder()
                .url(url)
                .build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val gson = Gson()
                if (response.isSuccessful && response.body() != null) {
                    val search = gson.fromJson(response.body()!!.string(), WikiGeoSearch::class.java)

                    search?.query?.geoSearch?.sortedBy { it.dist }
                    say("I found these nearby:")
                    listOptions.clear()
                    search?.query?.geoSearch?.forEach {
                        Timber.d("${it.title} - ${it.dist}")
                        if (it.title != null) {
                            val action = {
                                textToSpeech?.stop()
                                handleCommand(it.title)
                            }
                            listOptions.add(
                                    Pair(it.title, action)
                            )
                        }
                    }
                    runOnUiThread {
                        listAdapter.notifyDataSetInvalidated()
                    }


                } else {
                    Timber.e("Error getting location call")
                }
            }

            override fun onFailure(call: Call?, e: IOException?) {
                Timber.e(e)
            }

        })
    }

    fun startTalking(wikiUrl: String, originalQuery: String) {
        getHtml(wikiUrl, {
            doc = Jsoup.parse(it)

            when (getPageType()) {
                PageType.SEARCH -> {
                    say("I couldn't find anything matching \"$originalQuery\". Here are some search results.")
                }
                PageType.ARTICLE -> {
                    say(getPageTitle())
                    say(getSummary())
                    say("Page Contents")
                    say(getContentsTitles())
                }
                PageType.NEARBY -> {

                }
                else -> {
//                    say("I couldn't understand the command \"$originalQuery\".")
                }
            }
//            say("Purchase WikiPod Premium to enable voice navigation")
        })
    }

    fun initializeTts(){
        textToSpeech = TextToSpeech(this, TextToSpeech.OnInitListener {
            val result = it
            textToSpeech?.let {
                it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onDone(utteranceId: String?) {
                        Timber.d("OnDone: $utteranceId")
                        ttsIsSpeaking = false
                    }

                    override fun onError(utteranceId: String?) {
                    }

                    override fun onStart(utteranceId: String?) {
                        Timber.d("onStart: $utteranceId")
                        ttsIsSpeaking = true
                    }
                })
                // if the language is not available
                it.language = Locale.ENGLISH
                if (it.isLanguageAvailable(Locale.ENGLISH) < TextToSpeech.LANG_AVAILABLE) {
                    Toast.makeText(MainActivity@ this, "Please install the English TTS data set.", Toast.LENGTH_LONG).show()
                    val installIntent = Intent()
                    installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
                    startActivity(installIntent)
                } else {
                    if (result == TextToSpeech.SUCCESS) {
                        ttsIsInitialized = true
                        sayAll()
                    } else if (result == TextToSpeech.ERROR) {
                        Timber.e("Error initializing TTS")
                    }
                }
            }

        })
    }

    fun say(s: String) {
        // reduce string size so the tts works
        s.spliceSentences().forEach {
            thingsToSay.add(it)
        }

        if (textToSpeech == null) {
            initializeTts()
        } else if (ttsIsInitialized) {
            sayAll()
        }
    }

    fun sayAll() {
        while (thingsToSay.isNotEmpty()) {
            val s = thingsToSay.poll()
            Timber.d("Saying: $s")
            val hm = HashMap<String, String>()
            hm[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = s
            val status = textToSpeech?.speak(s, TextToSpeech.QUEUE_ADD, hm)
            if (status != TextToSpeech.SUCCESS) {
                Timber.e("Error queuing TTS: $status")
                Toast.makeText(this, "An error occurred while speaking the text.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getPageType(): PageType {
        if (doc.select("#section_0").text().contains("Search results")) {
            return PageType.SEARCH
        } else if (doc.select("#section_0").text() == ("Nearby")) {
            return PageType.NEARBY
        }
        return PageType.ARTICLE
    }

    fun getHtml(url: String, callback: (String) -> Unit) {
//        webView.loadUrl(url)
        getPage(url, callback)
    }

    val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

        }

        override fun onSensorChanged(event: SensorEvent?) {
            val distance = event?.values!!.first()
            Timber.d(distance.toString())
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    fun getPageTitle(): String {
        val element = doc.select("#section_0")
        return element.text()
    }

    fun getSummary(): String {
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
        var s: String = ""
        listOptions.clear()
        elements.eachText().forEachIndexed({ i: Int, sectionName: String ->
            s += sectionName + ". "
            listOptions.add(Pair(sectionName, {
                val sectionContent = doc.select(".mf-section-${i + 1}")
                sectionContent.select(".thumb").remove()
                textToSpeech?.stop()
                say(sectionName)
                say(sectionContent.text().stripNonProunouncable())
            }))
        })
        listAdapter.notifyDataSetInvalidated()
        return s
    }

    /**
     * Reduce so that all sentences are below max for tts
     */
    fun String.spliceSentences(): Array<String> {
        val new = ArrayList<String>()
        if (this.count() < TextToSpeech.getMaxSpeechInputLength()) {
            new.add(this)
            return new.toTypedArray()
        }

        var i = this.count() / 2
        var searching = true
        while (searching && i < this.count() - 2) {
            if (this[i] == '.' && this[i + 1] == ' ') {
                substring(0, i + 1).spliceSentences().forEach {
                    new.add(it)
                }
                substring(i + 1, this.count()).spliceSentences().forEach {
                    new.add(it)
                }
                searching = false
            }
            i++
        }

        return new.toTypedArray()
    }

    fun String.stripNonProunouncable(): String {
        val pairs = hashMapOf<Char, Char>(
                '(' to ')',
                '{' to '}',
                '[' to ']'
        )

        val builder = StringBuilder()
        var closer: Char? = null
        for (c in this) {
            if (pairs.containsKey(c)) {
                closer = pairs[c]
            }
            if (closer == null) {
                builder.append(c)
            }
            if (c == closer) {
                closer = null
            }
        }
        return builder.toString()
    }

    fun getPage(url: String, callback: (String) -> Unit) {
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

            }
        })
    }

    /*

                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.RECORD_AUDIO
     */

}
