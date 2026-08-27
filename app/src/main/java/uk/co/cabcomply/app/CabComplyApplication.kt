package uk.co.cabcomply.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.security.AppLockManager
import uk.co.cabcomply.app.data.seed.DatabaseSeeder
import javax.inject.Inject

@HiltAndroidApp
class CabComplyApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var databaseSeeder: DatabaseSeeder
    @Inject lateinit var appLockManager: AppLockManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockManager)
        applicationScope.launch {
            databaseSeeder.seedIfNeeded()
        }
    }
}
