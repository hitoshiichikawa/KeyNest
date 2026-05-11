package com.example.keynest.di

import android.content.Context

/**
 * Lightweight DI container.
 *
 * Concrete singletons (database, repository, cipher, use cases) are wired in
 * task T7.1 once the underlying types are introduced. This stub exists so that
 * [com.example.keynest.KeyNestApp.onCreate] can initialize the graph from task
 * T1.4 onward without forcing a circular dependency on later tasks.
 */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    /**
     * Idempotent initialization. Safe to call multiple times (later T7.1 work
     * will overwrite with the full singleton graph).
     */
    @Synchronized
    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    /**
     * Returns the application context registered via [initialize]. Intended for
     * later tasks (T7.1) that wire repository/database. Throws when accessed
     * before [initialize] has been called.
     */
    fun requireAppContext(): Context = requireNotNull(appContext) {
        "ServiceLocator.initialize() must be called from KeyNestApp.onCreate() before access"
    }
}
