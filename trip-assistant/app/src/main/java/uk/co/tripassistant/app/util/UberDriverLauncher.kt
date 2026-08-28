package uk.co.tripassistant.app.util

import android.content.Context
import android.content.Intent

/**
 * Opens the Uber Driver app, if it is installed (spec section 36).
 *
 * This is the whole extent of this app's interaction with Uber: a launch intent, started because
 * the driver tapped a button. Nothing is automated, nothing is navigated, no control is pressed
 * (spec sections 2 and 58).
 */
object UberDriverLauncher {

    private const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"

    fun isInstalled(context: Context): Boolean = launchIntent(context) != null

    /** @return true when the app was opened. */
    fun open(context: Context): Boolean {
        val intent = launchIntent(context) ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    private fun launchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(UBER_DRIVER_PACKAGE)
}
