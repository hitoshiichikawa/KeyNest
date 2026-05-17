package inc.goodanswers.keynest

import android.app.Application
import inc.goodanswers.keynest.di.ServiceLocator

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
