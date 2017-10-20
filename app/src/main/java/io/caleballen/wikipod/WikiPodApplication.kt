package io.caleballen.wikipod

import android.app.Application
import timber.log.Timber

/**
 * Created by caleb on 10/19/2017.
 */
class WikiPodApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}