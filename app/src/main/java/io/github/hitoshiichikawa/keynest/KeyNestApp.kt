package io.github.hitoshiichikawa.keynest

import android.app.Application
import io.github.hitoshiichikawa.keynest.di.ServiceLocator

/**
 * Application entry point. Initializes the [ServiceLocator] which holds the
 * singleton graph of database, repository, cipher, signature resolver and
 * use cases used by both UI and the AutofillService process.
 */
class KeyNestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
    }
}
