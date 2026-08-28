package uk.co.tripassistant.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * The "display over other apps" permission (spec section 24).
 *
 * Opening the right Settings screen is less reliable than it looks. The documented intent does not
 * resolve on every device — some manufacturers move the toggle into their own permissions screen,
 * and a few builds reject the `package:` form — so this tries progressively broader targets rather
 * than failing silently, which is what the first version did: the driver tapped "Allow" and
 * nothing whatsoever happened.
 *
 * Android can also refuse the toggle outright. From Android 13, and much more broadly from
 * Android 15, a permission like this one is a *restricted setting* for any app installed outside
 * an app store: the switch is visible but greyed out until the driver opens the app's info page,
 * taps the overflow menu and chooses "Allow restricted settings". That is Android protecting the
 * user from sideloaded apps, and no amount of app-side code can bypass it — so the app explains
 * it instead (see [RESTRICTED_SETTINGS_HINT]).
 */
object OverlayPermission {

    /**
     * Shown next to the permission request. Worth stating up front: a greyed-out switch looks
     * like a broken app, and the real cause is two menus away.
     */
    const val RESTRICTED_SETTINGS_HINT =
        "If Android says \"App was denied access\", it has locked this permission because the app " +
            "was installed outside the Play Store — not because anything is wrong with it.\n\n" +
            "To unlock it, go to Settings › Apps › Trip Assistant — the app's own info page, not " +
            "the permission screen — tap ⋮ in the top corner and choose \"Allow restricted " +
            "settings\". Then come back and turn the switch on."

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /**
     * Opens the most specific Settings screen this device actually has.
     *
     * @return false when none of them could be opened, so the caller can say so instead of
     *   leaving the driver tapping a button that does nothing.
     */
    fun openSettings(context: Context): Boolean {
        val packageUri = Uri.parse("package:${context.packageName}")
        val candidates = listOf(
            // This app's own overlay toggle.
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri),
            // The full "Display over other apps" list — find the app in it.
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            // Last resort: the app's info page, which is where "Allow restricted settings" lives
            // anyway, and from which the driver can reach the permission on most skins.
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        )

        for (intent in candidates) {
            val opened = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (opened) return true
        }
        return false
    }
}
