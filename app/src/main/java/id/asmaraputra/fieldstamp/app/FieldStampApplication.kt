package id.asmaraputra.fieldstamp.app

import android.app.Application
import kotlinx.coroutines.launch

class FieldStampApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            container.files.clearTemp()
            runCatching { container.photos.pruneOrphanFiles() }
        }
        // Refreshes product details and re-verifies an owned purchase when Play is reachable.
        // Offline, this simply fails and the cached entitlement applies.
        container.billing.refresh()
    }
}
