package io.caleballen.wikipod

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

class MainActivity : AppCompatActivity() {
    var textToSpeech : TextToSpeech? = null
    var ttsIsInitialized = false
    lateinit var httpClient : OkHttpClient

    lateinit var doc : Document

    val thingsToSay : Queue<String> = LinkedBlockingQueue()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getHtml("https://en.m.wikipedia.org/wiki/Agnes_Taylor", {
            val html = it
            doc = Jsoup.parse(html)
            say(getPageTitle())
            say(getSummary())
            say("Page Contents")
            say(getContentsTitles())
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
        httpClient = OkHttpClient()
        webView.loadUrl(url)
        getPage(url, callback)
    }

    override fun onPause() {
        super.onPause()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
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
}
