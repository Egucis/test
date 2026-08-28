package uk.co.tripassistant.app.ui.navigation

object Destinations {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SUBSCRIPTION = "subscription"
    const val PROFILES = "profiles"
    const val PROFILE_EDIT = "profiles/{profileId}"
    const val RULE_TESTER = "rule-tester"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{offerId}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val DIAGNOSTICS = "diagnostics"

    fun profileEdit(id: Long) = "profiles/$id"
    fun historyDetail(id: Long) = "history/$id"
}
