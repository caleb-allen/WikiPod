package io.caleballen.wikipod

import android.app.Application
import com.google.android.gms.ads.MobileAds
import timber.log.Timber

/**
 * Created by caleb on 10/19/2017.
 */
class WikiPodApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        MobileAds.initialize(this, "ca-app-pub-5438694514082087~8674998037")
    }
}